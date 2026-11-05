package es.florida.vet.odoo;

import es.florida.vet.repository.PagoRepository;
import es.florida.vet.service.AutorizacionPedido;

import es.florida.vet.entity.Clinica;
import es.florida.vet.entity.Firma;
import es.florida.vet.entity.OdooLog;
import es.florida.vet.entity.Pedido;
import es.florida.vet.entity.Producto;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.repository.ClinicaRepository;
import es.florida.vet.repository.OdooLogRepository;
import es.florida.vet.repository.PedidoRepository;
import es.florida.vet.repository.ProductoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orquesta la sincronización con Odoo usando el ADAPTADOR configurado
 * (SIMULADO por defecto, REAL con vetstock.odoo.modo=REAL).
 *
 * Cada operación queda registrada en la tabla odoo_log. La exportación de un
 * pedido se ejecuta en su propia transacción para poder invocarla desde un
 * listener AFTER_COMMIT: un rechazo de Odoo nunca revierte el pago ni la firma.
 */
@Service
public class OdooSincronizacionService {

    private static final int MAX_ERROR_PEDIDO = 255;

    private final OdooConnector odoo;
    private final ProductoRepository productoRepository;
    private final ClinicaRepository clinicaRepository;
    private final PedidoRepository pedidoRepository;
    private final OdooLogRepository odooLogRepository;
    private final PagoRepository pagoRepository;
    private final es.florida.vet.repository.BultoRepository bultoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${vetstock.odoo.modo:SIMULADO}")
    private String modo;

    public OdooSincronizacionService(OdooConnector odoo,
                                     ProductoRepository productoRepository,
                                     ClinicaRepository clinicaRepository,
                                     PedidoRepository pedidoRepository,
                                     OdooLogRepository odooLogRepository,
                                     PagoRepository pagoRepository,
                                     es.florida.vet.repository.BultoRepository bultoRepository) {
        this.odoo = odoo;
        this.productoRepository = productoRepository;
        this.clinicaRepository = clinicaRepository;
        this.pedidoRepository = pedidoRepository;
        this.odooLogRepository = odooLogRepository;
        this.pagoRepository = pagoRepository;
        this.bultoRepository = bultoRepository;
    }

    @Transactional
    public Map<String, Object> probarConexion() {
        OdooConnector.Resultado r = odoo.probarConexion();
        odooLogRepository.save(new OdooLog("CONEXION", modo, r.exito(), r.detalle(), r.odooId()));
        return resumen("CONEXION", r);
    }

    /** Sincroniza TODO el catálogo activo con Odoo. Idempotente por código. */
    @Transactional
    public Map<String, Object> sincronizarProductos() {
        List<Producto> productos = productoRepository.findAllByOrderByNombreAsc();
        int creados = 0, actualizados = 0, fallos = 0;
        LocalDateTime ahora = LocalDateTime.now();
        for (Producto producto : productos) {
            if (!producto.isActivo()) {
                continue;
            }
            OdooConnector.Resultado r = odoo.crearProducto(new OdooConnector.FichaProducto(
                    producto.getCodigo(), producto.getNombre(), producto.getDescripcion(), producto.getPrecio()));
            if (r.exito()) {
                if (producto.getOdooId() == null) {
                    creados++;
                } else {
                    actualizados++;
                }
                producto.setOdooId(r.odooId());
                producto.setOdooSincronizado(ahora);
                productoRepository.save(producto);
            } else {
                fallos++;
            }
            odooLogRepository.save(new OdooLog("PRODUCTO", producto.getCodigo(),
                    r.exito(), r.detalle(), r.odooId()));
        }
        return resumenProductos(creados, actualizados, fallos, productos.size());
    }

    /** Sincroniza las clínicas como clientes (res.partner) de Odoo. */
    @Transactional
    public Map<String, Object> sincronizarClientes() {
        List<Clinica> clinicas = clinicaRepository.findAll();
        int creados = 0, actualizados = 0, fallos = 0;
        LocalDateTime ahora = LocalDateTime.now();
        for (Clinica clinica : clinicas) {
            if (!clinica.isActiva()) {
                continue;
            }
            OdooConnector.Resultado r = odoo.crearCliente(new OdooConnector.FichaCliente(
                    clinica.getNombre(), clinica.getDireccion(), clinica.getTelefono(),
                    nombreResponsable(clinica), clinica.getOdooId()));
            if (r.exito()) {
                if (clinica.getOdooId() == null) {
                    creados++;
                } else {
                    actualizados++;
                }
                clinica.setOdooId(r.odooId());
                clinica.setOdooSincronizado(ahora);
                clinicaRepository.save(clinica);
            } else {
                fallos++;
            }
            odooLogRepository.save(new OdooLog("CLIENTE", clinica.getNombre(),
                    r.exito(), r.detalle(), r.odooId()));
        }
        Map<String, Object> datos = baseResumen("CLIENTES");
        datos.put("creados", creados);
        datos.put("actualizados", actualizados);
        datos.put("fallos", fallos);
        return datos;
    }

    /**
     * Exporta o actualiza el sale.order al confirmarse el pago. No depende de
     * EXPEDIDO: PENDIENTE_FIRMA pagado se puede publicar y una firma posterior
     * vuelve a escribir la nota del mismo pedido de Odoo.
     *
     * El bloqueo pesimista serializa dos eventos de pago/firma que lleguen a la
     * vez para el mismo pedido. Se usa EntityManager porque el repositorio no
     * expone un método de lock específico.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> enviarPedido(Long pedidoId) {
        Pedido pedido = entityManager.find(Pedido.class, pedidoId, LockModeType.PESSIMISTIC_WRITE);
        if (pedido == null) {
            throw new RecursoNoEncontradoException("El pedido no existe");
        }
        if (pedido.getFechaPago() == null) {
            // El envío manual mantiene una respuesta explícita si el pago aún no está confirmado.
            throw new ReglaNegocioException("Solo se exportan a Odoo los pedidos con el pago confirmado");
        }

        try {
            // Aseguramos que el cliente exista y se actualice antes de crear/actualizar el pedido.
            Clinica clinica = pedido.getClinica();
            OdooConnector.Resultado rCliente = odoo.crearCliente(new OdooConnector.FichaCliente(
                    clinica.getNombre(), clinica.getDireccion(), clinica.getTelefono(),
                    nombreResponsable(clinica), clinica.getOdooId()));
            if (!rCliente.exito() || rCliente.odooId() == null) {
                return registrarFallo(pedido, "Fallo al sincronizar el cliente: " + detalle(rCliente));
            }
            clinica.setOdooId(rCliente.odooId());
            clinica.setOdooSincronizado(LocalDateTime.now());
            clinicaRepository.save(clinica);

            // No se toca ninguna transición de stock o logística. Solo se pone el catálogo al día.
            sincronizarProductos();

            List<OdooConnector.LineaPedido> lineas = pedido.getLineas().stream()
                    .map(l -> new OdooConnector.LineaPedido(
                            l.getProducto().getCodigo(), l.getCantidad(), l.getPrecioUnitario()))
                    .toList();
            OdooConnector.Resultado rPedido = odoo.crearPedido(fichaPedido(pedido, clinica.getOdooId(), lineas));
            if (!rPedido.exito() || rPedido.odooId() == null) {
                return registrarFallo(pedido, "Odoo rechazó el pedido: " + detalle(rPedido));
            }

            // En una actualización el conector devuelve el id existente, nunca crea un segundo sale.order.
            pedido.setOdooId(rPedido.odooId());
            pedido.setOdooEnviado(LocalDateTime.now());
            pedido.setOdooSincronizacionPendiente(false);
            pedido.setOdooUltimoError(null);
            pedidoRepository.save(pedido);
            odooLogRepository.save(new OdooLog("PEDIDO", pedido.getId().toString(),
                    true, detalle(rPedido), rPedido.odooId()));
            return resumenPedido(pedido, true, detalle(rPedido), rPedido.odooId());
        } catch (RuntimeException e) {
            // El adaptador suele devolver Resultado.fallo; esta protección cubre errores de transporte imprevistos.
            return registrarFallo(pedido, "Error al sincronizar con Odoo: " + detalle(e));
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estado() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("modo", modo);
        datos.put("productoSincronizados", productoRepository.findAllByOrderByNombreAsc().stream()
                .filter(p -> p.getOdooId() != null).count());
        datos.put("productosTotales", productoRepository.findAllByOrderByNombreAsc().stream()
                .filter(Producto::isActivo).count());
        datos.put("clientesSincronizados", clinicaRepository.findAll().stream()
                .filter(c -> c.getOdooId() != null).count());
        datos.put("pedidosExportados", pedidoRepository.findAll().stream()
                .filter(p -> p.getOdooId() != null).count());
        return datos;
    }

    @Transactional(readOnly = true)
    public List<OdooLog> log() {
        return odooLogRepository.findTop50ByOrderByIdDesc();
    }

    // ----- helpers -----

    private OdooConnector.FichaPedido fichaPedido(Pedido pedido, Integer odooClienteId,
                                                   List<OdooConnector.LineaPedido> lineas) {
        Firma firma = pedido.getFirma();
        // Misma regla que la preparación: firma del veterinario asignado sobre el contenido actual
        AutorizacionPedido autorizacion = AutorizacionPedido.de(pedido, importeConfirmado(pedido));
        boolean firmado = autorizacion.firmaValida();
        String firmanteNombre = firmado && firma.getUsuario() != null
                ? firma.getUsuario().getNombreCompleto() : null;
        String firmanteRol = firmado && firma.getUsuario() != null && firma.getUsuario().getRol() != null
                ? firma.getUsuario().getRol().name() : null;
        String numeroColegiado = firmado ? firma.getNumeroColegiado() : null;
        String estadoPedido = pedido.getEstado() == null ? null : pedido.getEstado().name();
        es.florida.vet.service.PesoPedido peso = es.florida.vet.service.PesoPedido.de(pedido);

        return new OdooConnector.FichaPedido("VSM-" + pedido.getId(), odooClienteId, lineas,
                pedido.getOdooId(), firmado ? "FIRMADO" : "PENDIENTE",
                nombreResponsable(pedido), firmanteNombre, firmanteRol, numeroColegiado, estadoPedido,
                pedido.getIvaPorcentaje(), pedido.getBaseImponible(), pedido.getImporteIva(),
                pedido.getTotal(), pedido.getMoneda(), autorizacion.pagoCompleto(), autorizacion.firmaValida(),
                pedido.getCreadoPor() == null ? null : pedido.getCreadoPor().getNombreCompleto(),
                // VetStock es la fuente: bultos calculados por peso (vacío si faltan pesos),
                // salida asignada (o vacía) y peso total (solo si está completo)
                peso.bultosCalculados(),
                pedido.getDestino() == null ? null : pedido.getDestino().getEtiqueta(),
                peso.completo() ? peso.pesoTotalKg() : null,
                pedido.getClinica().getDireccion());
    }

    private java.math.BigDecimal importeConfirmado(Pedido pedido) {
        return pagoRepository.findByPedidoIdOrderByFechaInicioDesc(pedido.getId()).stream()
                .filter(p -> p.getEstado() == es.florida.vet.enums.EstadoPago.CONFIRMADO)
                .map(es.florida.vet.entity.Pago::getImporte)
                .findFirst().orElse(null);
    }

    private Map<String, Object> registrarFallo(Pedido pedido, String detalle) {
        String detalleSeguro = limitar(detalle);
        pedido.setOdooSincronizacionPendiente(true);
        pedido.setOdooUltimoError(detalleSeguro);
        pedidoRepository.save(pedido);
        odooLogRepository.save(new OdooLog("PEDIDO", pedido.getId().toString(),
                false, detalleSeguro, pedido.getOdooId()));
        return resumenPedido(pedido, false, detalleSeguro, pedido.getOdooId());
    }

    private String limitar(String texto) {
        String seguro = (texto == null || texto.isBlank()) ? "Error sin detalle de Odoo" : texto;
        return seguro.length() <= MAX_ERROR_PEDIDO ? seguro : seguro.substring(0, MAX_ERROR_PEDIDO);
    }

    private String detalle(OdooConnector.Resultado resultado) {
        return resultado == null ? "Odoo no devolvió resultado" : detalle(resultado.detalle());
    }

    private String detalle(Throwable error) {
        return detalle(error == null ? null : error.getMessage());
    }

    private String detalle(String detalle) {
        return (detalle == null || detalle.isBlank()) ? "Sin detalle" : detalle;
    }

    private Map<String, Object> resumen(String tipo, OdooConnector.Resultado r) {
        Map<String, Object> datos = baseResumen(tipo);
        datos.put("exito", r.exito());
        datos.put("detalle", detalle(r));
        datos.put("odooId", r.odooId());
        return datos;
    }

    private Map<String, Object> resumenProductos(int creados, int actualizados, int fallos, int total) {
        Map<String, Object> datos = baseResumen("PRODUCTOS");
        datos.put("creados", creados);
        datos.put("actualizados", actualizados);
        datos.put("fallos", fallos);
        datos.put("total", total);
        return datos;
    }

    private Map<String, Object> resumenPedido(Pedido pedido, boolean exito, String detalle, Integer odooId) {
        Map<String, Object> datos = baseResumen("PEDIDO");
        datos.put("pedidoId", pedido.getId());
        datos.put("exito", exito);
        datos.put("detalle", detalle(detalle));
        datos.put("odooId", odooId);
        return datos;
    }

    private Map<String, Object> baseResumen(String tipo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("operacion", tipo);
        datos.put("modo", modo);
        return datos;
    }

    private String nombreResponsable(Clinica clinica) {
        return clinica.getVeterinarioResponsable() == null
                ? null
                : clinica.getVeterinarioResponsable().getUsuario().getNombreCompleto();
    }

    private String nombreResponsable(Pedido pedido) {
        return pedido.getVeterinario() == null || pedido.getVeterinario().getUsuario() == null
                ? null
                : pedido.getVeterinario().getUsuario().getNombreCompleto();
    }
}
