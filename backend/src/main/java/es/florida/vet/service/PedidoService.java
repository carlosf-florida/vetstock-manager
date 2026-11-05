package es.florida.vet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.florida.vet.dto.FirmaRequest;
import es.florida.vet.dto.LineaPedidoRequest;
import es.florida.vet.dto.LineasRequest;
import es.florida.vet.dto.PedidoRequest;
import es.florida.vet.dto.PedidoResponse;
import es.florida.vet.entity.Auxiliar;
import es.florida.vet.entity.Bulto;
import es.florida.vet.entity.Clinica;
import es.florida.vet.entity.Firma;
import es.florida.vet.entity.LineaPedido;
import es.florida.vet.entity.MovimientoStock;
import es.florida.vet.entity.Pedido;
import es.florida.vet.entity.Producto;
import es.florida.vet.entity.Usuario;
import es.florida.vet.entity.Veterinario;
import es.florida.vet.enums.EstadoPedido;
import es.florida.vet.enums.TipoFirma;
import es.florida.vet.enums.TipoMovimiento;
import es.florida.vet.exception.AccesoDenegadoException;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.repository.BultoRepository;
import es.florida.vet.repository.PagoRepository;
import es.florida.vet.entity.Pago;
import es.florida.vet.enums.EstadoPago;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import es.florida.vet.repository.AuxiliarRepository;
import es.florida.vet.repository.FirmaRepository;
import es.florida.vet.repository.MovimientoStockRepository;
import es.florida.vet.repository.PedidoRepository;
import es.florida.vet.repository.ProductoRepository;
import es.florida.vet.repository.VeterinarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lógica de los pedidos. Es la clase más importante del sistema porque
 * aquí viven las reglas de negocio:
 *
 *  - Los precios los calcula SIEMPRE el servidor (BigDecimal), nunca el frontend.
 *  - El stock se reserva de forma ATÓMICA al enviar a firma.
 *  - Solo firma el veterinario asignado al pedido, con su propia cuenta.
 *  - Al firmar se guarda una copia inmutable del contenido autorizado.
 *  - Firma y pago en cualquier orden; LISTO_PREPARAR exige firma VÁLIDA y pago completo.
 *  - Las reservas sin pago caducan (ver ReservaCaducidadTask).
 *  - El pago confirmado solicita actualizar Odoo después del commit.
 *  - El stock físico se descuenta UNA SOLA VEZ, al expedir.
 */
@Service
public class PedidoService {

    private static final int MAX_BYTES_FIRMA = 5 * 1024 * 1024;   // 5 MB

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final AuxiliarRepository auxiliarRepository;
    private final MovimientoStockRepository movimientoRepository;
    private final FirmaRepository firmaRepository;
    private final BultoRepository bultoRepository;
    private final PagoRepository pagoRepository;
    private final SesionService sesionService;
    private final ApplicationEventPublisher eventos;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PedidoService(PedidoRepository pedidoRepository,
                         ProductoRepository productoRepository,
                         VeterinarioRepository veterinarioRepository,
                         AuxiliarRepository auxiliarRepository,
                         MovimientoStockRepository movimientoRepository,
                         FirmaRepository firmaRepository,
                         BultoRepository bultoRepository,
                         PagoRepository pagoRepository,
                         SesionService sesionService,
                         ApplicationEventPublisher eventos) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.auxiliarRepository = auxiliarRepository;
        this.movimientoRepository = movimientoRepository;
        this.firmaRepository = firmaRepository;
        this.bultoRepository = bultoRepository;
        this.pagoRepository = pagoRepository;
        this.sesionService = sesionService;
        this.eventos = eventos;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PedidoResponse> listar(EstadoPedido estado, Long clinicaFiltro,
                                       String username, boolean esAdmin) {
        Usuario usuario = sesionService.usuarioActual(username);
        List<Pedido> pedidos;
        if (esAdmin) {
            pedidos = (clinicaFiltro == null)
                    ? pedidoRepository.findAllByOrderByFechaCreacionDesc()
                    : pedidoRepository.findByClinicaIdOrderByFechaCreacionDesc(clinicaFiltro);
        } else {
            Long clinica = clinicaDelCliente(usuario);
            pedidos = pedidoRepository.findByClinicaIdOrderByFechaCreacionDesc(clinica);
            // Aunque pidan otra clínica, solo ven la suya
            if (clinicaFiltro != null && !clinicaFiltro.equals(clinica)) {
                pedidos = List.of();
            }
        }
        // Un solo acceso a pagos para toda la lista (no una consulta por pedido)
        Map<Long, BigDecimal> confirmados = new HashMap<>();
        for (Pago pago : pagoRepository.findByEstado(EstadoPago.CONFIRMADO)) {
            confirmados.put(pago.getPedido().getId(), pago.getImporte());
        }
        // Recuento de bultos de todos los pedidos en una sola consulta agregada
        Map<Long, Long> bultos = new HashMap<>();
        for (Object[] fila : bultoRepository.contarPorPedido()) {
            bultos.put((Long) fila[0], (Long) fila[1]);
        }
        return pedidos.stream()
                .filter(p -> estado == null || p.getEstado() == estado)
                .map(p -> PedidoResponse.desde(p, AutorizacionPedido.de(p, confirmados.get(p.getId())),
                        bultos.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long id, String username, boolean esAdmin) {
        Pedido pedido = buscarPedido(id);
        comprobarAccesoAClinica(pedido, username, esAdmin);
        return respuesta(pedido);
    }

    // ------------------------------------------------------------------
    // Creación y edición (solo en BORRADOR)
    // ------------------------------------------------------------------

    @Transactional
    public PedidoResponse crear(PedidoRequest datos, String username, boolean esAdmin) {
        Usuario usuario = sesionService.usuarioActual(username);
        Veterinario firmante;

        switch (usuario.getRol()) {
            case ADMIN -> {
                // El ADMIN puede crear pedidos EN NOMBRE de un cliente; queda registrado
                if (datos.clinicaId() == null || datos.veterinarioId() == null) {
                    throw new ReglaNegocioException("Como administrador debes indicar clínica y veterinario firmante");
                }
                firmante = veterinarioRepository.findById(datos.veterinarioId())
                        .orElseThrow(() -> new RecursoNoEncontradoException("El veterinario no existe"));
                if (!firmante.getClinica().getId().equals(datos.clinicaId())) {
                    throw new ReglaNegocioException("El veterinario no pertenece a esa clínica");
                }
            }
            case VETERINARIO -> {
                Veterinario propio = buscarVeterinarioPorUsername(username);
                firmante = propio.getClinica().getVeterinarioResponsable();
                if (firmante == null) {
                    throw new ReglaNegocioException("Tu clínica todavía no tiene un veterinario responsable asignado");
                }
            }
            case AUXILIAR -> {
                Auxiliar auxiliar = auxiliarRepository.findByUsuarioUsername(username)
                        .orElseThrow(() -> new AccesoDenegadoException("Tu cuenta no está asociada a ninguna clínica"));
                firmante = auxiliar.getClinica().getVeterinarioResponsable();
                if (firmante == null) {
                    throw new ReglaNegocioException("Tu clínica todavía no tiene un veterinario responsable asignado");
                }
            }
            default -> throw new AccesoDenegadoException("Rol no permitido");
        }

        if (!firmante.getClinica().isActiva()) {
            throw new ReglaNegocioException("La clínica no está activa");
        }

        Pedido pedido = new Pedido(firmante.getClinica(), firmante, usuario);
        pedido.setObservaciones(datos.observaciones());
        pedido.reemplazarLineas(construirLineas(datos.lineas()));
        pedido.registrarModificacion(usuario);
        return respuesta(pedidoRepository.save(pedido));
    }

    /** Reemplaza las líneas de un pedido que sigue en BORRADOR. */
    @Transactional
    public PedidoResponse modificarLineas(Long id, LineasRequest datos, String username, boolean esAdmin) {
        Pedido pedido = buscarPedido(id);
        Usuario usuario = sesionService.usuarioActual(username);
        boolean esCreador = pedido.getCreadoPor().getUsername().equals(username);
        if (!esAdmin && !esCreador) {
            throw new AccesoDenegadoException("Solo el creador del pedido o administración pueden modificarlo");
        }
        if (pedido.getEstado() != EstadoPedido.BORRADOR) {
            throw new ReglaNegocioException("Solo se puede modificar un pedido en BORRADOR");
        }
        pedido.reemplazarLineas(construirLineas(datos.lineas()));
        pedido.registrarModificacion(usuario);
        return respuesta(pedidoRepository.save(pedido));
    }

    /** Valida productos, cantidades y precios; los precios los pone el servidor. */
    private List<LineaPedido> construirLineas(List<LineaPedidoRequest> lineas) {
        Set<Long> productosAnadidos = new HashSet<>();
        List<LineaPedido> resultado = new ArrayList<>();
        for (LineaPedidoRequest linea : lineas) {
            Producto producto = productoRepository.findById(linea.productoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "El producto con id " + linea.productoId() + " no existe"));
            if (!producto.isActivo()) {
                throw new ReglaNegocioException("El producto " + producto.getNombre() + " no está disponible");
            }
            if (!productosAnadidos.add(producto.getId())) {
                throw new ReglaNegocioException("El producto " + producto.getNombre() + " aparece más de una vez");
            }
            resultado.add(new LineaPedido(null, producto, linea.cantidad(), producto.getPrecio()));
        }
        return resultado;
    }

    // ------------------------------------------------------------------
    // Enviar a firma: reserva atómica de stock
    // ------------------------------------------------------------------

    @Transactional
    public PedidoResponse enviarFirma(Long id, String username, boolean esAdmin) {
        Pedido pedido = buscarPedido(id);
        comprobarAccesoAClinica(pedido, username, esAdmin);
        comprobarTransicion(pedido, EstadoPedido.PENDIENTE_FIRMA);

        // Reserva atómica: si una línea falla, la transacción entera se deshace
        for (LineaPedido linea : pedido.getLineas()) {
            int filas = productoRepository.reservar(linea.getProducto().getId(), linea.getCantidad());
            if (filas == 0) {
                throw new ReglaNegocioException("Stock insuficiente de " + linea.getProducto().getNombre()
                        + ": disponible " + linea.getProducto().getStockDisponible()
                        + ", solicitado " + linea.getCantidad());
            }
        }
        pedido.setEstado(EstadoPedido.PENDIENTE_FIRMA);
        pedido.setFechaEnvioFirma(LocalDateTime.now());
        return respuesta(pedidoRepository.save(pedido));
    }

    // ------------------------------------------------------------------
    // Firma del veterinario asignado, con copia inmutable
    // ------------------------------------------------------------------

    @Transactional
    public PedidoResponse firmar(Long id, FirmaRequest datos, String username) {
        Pedido pedido = buscarPedido(id);
        Usuario firmante = sesionService.usuarioActual(username);
        comprobarAccesoAClinica(pedido, username, false);
        boolean esVeterinarioAsignado = firmante.getRol() == es.florida.vet.enums.Rol.VETERINARIO
                && pedido.getVeterinario().getUsuario().getUsername().equals(username);
        if (!esVeterinarioAsignado) {
            throw new AccesoDenegadoException("Solo el veterinario asignado al pedido puede firmarlo");
        }
        // Se puede firmar antes o después del pago, pero nunca con la preparación empezada
        List<EstadoPedido> firmables = List.of(EstadoPedido.PENDIENTE_FIRMA, EstadoPedido.FIRMADO, EstadoPedido.LISTO_PREPARAR);
        if (!firmables.contains(pedido.getEstado())) {
            throw new ReglaNegocioException("El pedido no se puede firmar en su estado actual");
        }
        Firma anterior = firmaRepository.findByPedidoId(id).orElse(null);
        if (anterior != null && AutorizacionPedido.motivoFirmaNoValida(pedido) == null) {
            throw new ReglaNegocioException("El pedido ya tiene una firma válida del veterinario asignado");
        }
        if (datos.confirmacion() != Boolean.TRUE) {
            throw new ReglaNegocioException("Debes confirmar el pedido con tu propia identidad");
        }
        String colegiado = datos.numeroColegiado() == null ? null : datos.numeroColegiado().trim();
        if (colegiado != null && colegiado.isBlank()) colegiado = null;
        if (colegiado != null && !colegiado.equals(pedido.getVeterinario().getNumeroColegiado())) {
            throw new ReglaNegocioException("El colegiado indicado no coincide con el veterinario responsable; es opcional, no una clave de firma");
        }
        byte[] imagen = decodificarImagen(datos.imagenBase64(), datos.tipoFirma(), datos.nombreArchivo());
        String contenido = contenidoInmutable(pedido, firmante, colegiado,
                anterior == null ? null : anterior.getUsuario().getNombreCompleto());
        Firma firma;
        if (anterior == null) {
            firma = new Firma(pedido, firmante, contenido, tipoFirma(datos.tipoFirma()), imagen, datos.nombreArchivo());
        } else {
            // Una firma NO válida (de otra persona o de un contenido anterior) se sustituye; la copia
            // nueva guarda quién había firmado antes. Solo hay una firma por pedido.
            firma = anterior;
            firma.sustituir(firmante, contenido, tipoFirma(datos.tipoFirma()), imagen, datos.nombreArchivo());
        }
        firma.setNumeroColegiado(colegiado);
        firmaRepository.save(firma);
        pedido.setFirma(firma);
        pedido.setFechaFirma(firma.getFechaFirma());
        if (pedido.getFechaPago() != null) {
            // Ya estaba pagado: con la firma válida queda listo para preparar
            pedido.setEstado(EstadoPedido.LISTO_PREPARAR);
            if (pedido.getFechaListoPreparar() == null) pedido.setFechaListoPreparar(LocalDateTime.now());
            programarSincronizacion(pedido);
        } else {
            pedido.setEstado(EstadoPedido.FIRMADO);
        }
        return respuesta(pedidoRepository.save(pedido));
    }

    /**
     * El veterinario asignado rechaza un pedido que no ha firmado: se cancela y libera el stock.
     * Si ya estaba pagado queda marcado «devolución pendiente»: la devolución del dinero se
     * gestiona fuera de la aplicación (no hay reembolsos en este proyecto).
     */
    @Transactional
    public PedidoResponse rechazar(Long id, String motivo, String username) {
        Pedido pedido = buscarPedido(id);
        Usuario usuario = sesionService.usuarioActual(username);
        boolean esVeterinarioAsignado = usuario.getRol() == es.florida.vet.enums.Rol.VETERINARIO
                && pedido.getVeterinario().getUsuario().getUsername().equals(username);
        if (!esVeterinarioAsignado) {
            throw new AccesoDenegadoException("Solo el veterinario asignado puede rechazar el pedido");
        }
        List<EstadoPedido> rechazables = List.of(EstadoPedido.PENDIENTE_FIRMA, EstadoPedido.FIRMADO, EstadoPedido.LISTO_PREPARAR);
        if (!rechazables.contains(pedido.getEstado())) {
            throw new ReglaNegocioException("El pedido no se puede rechazar en su estado actual");
        }
        if (AutorizacionPedido.motivoFirmaNoValida(pedido) == null) {
            throw new ReglaNegocioException("Ya has autorizado este pedido con tu firma; no se puede rechazar");
        }
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.isEmpty() || texto.length() > 200) {
            throw new ReglaNegocioException("Indica el motivo del rechazo (máximo 200 caracteres)");
        }
        cancelarYLiberar(pedido, "Rechazado por " + usuario.getNombreCompleto() + ": " + texto);
        if (pedido.getFechaPago() != null) {
            pedido.setDevolucionPendiente(true);
            programarSincronizacion(pedido);
        }
        return respuesta(pedidoRepository.save(pedido));
    }

    private byte[] decodificarImagen(String base64, String tipoFirma, String nombreArchivo) {
        if (base64 == null || base64.isBlank()) {
            throw new ReglaNegocioException("La firma está vacía");
        }
        byte[] bytes;
        try {
            String limpio = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
            bytes = java.util.Base64.getDecoder().decode(limpio);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException("La imagen de la firma no es válida");
        }
        if (bytes.length > MAX_BYTES_FIRMA) {
            throw new ReglaNegocioException("La imagen de la firma supera los 5 MB permitidos");
        }
        // Comprobamos que es un PNG o un JPG por sus bytes de cabecera
        boolean esPng = bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean esJpg = bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
        if (!esPng && !esJpg) {
            throw new ReglaNegocioException("La firma debe ser una imagen PNG o JPG");
        }
        if ("IMAGEN".equals(tipoFirma) && (nombreArchivo == null || nombreArchivo.isBlank())) {
            throw new ReglaNegocioException("Falta el nombre del archivo de la firma");
        }
        return bytes;
    }

    private TipoFirma tipoFirma(String tipo) {
        try {
            return TipoFirma.valueOf(tipo);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ReglaNegocioException("El tipo de firma no es válido");
        }
    }

    /** Copia inmutable del contenido autorizado, en JSON, para que quede registro. */
    private String contenidoInmutable(Pedido pedido, Usuario firmante, String colegiado, String sustituyeFirmaDe) {
        try {
            var contenido = new java.util.LinkedHashMap<String, Object>();
            contenido.put("pedidoId", pedido.getId());
            contenido.put("clinica", pedido.getClinica().getNombre());
            contenido.put("veterinarioResponsable", pedido.getVeterinario().getUsuario().getNombreCompleto());
            contenido.put("firmanteNombre", firmante.getNombreCompleto());
            contenido.put("firmanteUsername", firmante.getUsername());
            contenido.put("firmanteRol", firmante.getRol().name());
            contenido.put("numeroColegiadoIdentificativo", colegiado);
            contenido.put("tipoConfirmacion", "CONFIRMACION_INTERNA_NO_CERTIFICADA");
            if (sustituyeFirmaDe != null) contenido.put("sustituyeFirmaNoValidaDe", sustituyeFirmaDe);
            contenido.put("observaciones", pedido.getObservaciones());
            contenido.put("baseImponible", pedido.getBaseImponible().toPlainString());
            contenido.put("ivaPorcentaje", pedido.getIvaPorcentaje().toPlainString());
            contenido.put("importeIva", pedido.getImporteIva().toPlainString());
            contenido.put("moneda", pedido.getMoneda());
            contenido.put("total", pedido.getTotal().toPlainString());
            contenido.put("lineas", pedido.getLineas().stream().map(l -> {
                var linea = new java.util.LinkedHashMap<String, Object>();
                linea.put("codigo", l.getProducto().getCodigo());
                linea.put("producto", l.getProducto().getNombre());
                linea.put("cantidad", l.getCantidad());
                linea.put("precioUnitario", l.getPrecioUnitario().toPlainString());
                linea.put("importe", l.getImporte().toPlainString());
                return linea;
            }).toList());
            contenido.put("fechaAutorizacion", LocalDateTime.now().toString());
            return objectMapper.writeValueAsString(contenido);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("No se pudo generar la copia del pedido", e);
        }
    }

    @Transactional(readOnly = true)
    public Firma firmaDe(Long id, String username, boolean esAdmin) {
        Pedido pedido = buscarPedido(id);
        comprobarAccesoAClinica(pedido, username, esAdmin);
        return firmaRepository.findByPedidoId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido todavía no está firmado"));
    }

    @Transactional(readOnly = true)
    public byte[] imagenDeFirma(Long id, String username, boolean esAdmin) {
        Firma firma = firmaDe(id, username, esAdmin);
        if (firma.getImagen() == null) {
            throw new RecursoNoEncontradoException("La firma no tiene imagen");
        }
        return firma.getImagen();
    }

    // ------------------------------------------------------------------
    // Cancelación (libera la reserva si la hay)
    // ------------------------------------------------------------------

    @Transactional
    public PedidoResponse cancelar(Long id, String username, boolean esAdmin) {
        Pedido pedido = buscarPedido(id);
        comprobarAccesoAClinica(pedido, username, esAdmin);
        comprobarTransicion(pedido, EstadoPedido.CANCELADO);
        if (pedido.getFechaPago() != null) {
            throw new ReglaNegocioException("Un pedido pagado no se puede cancelar desde la aplicación");
        }
        Usuario usuario = sesionService.usuarioActual(username);
        cancelarYLiberar(pedido, "Cancelado por " + usuario.getNombreCompleto());
        return respuesta(pedidoRepository.save(pedido));
    }

    /**
     * Política de caducidad acordada: un pedido enviado a firma que no tiene
     * pago confirmado pasado el plazo se cancela y libera su reserva.
     * Si hay un pago iniciado hace poco (el cliente puede estar en la pasarela)
     * se espera a la siguiente pasada. Devuelve cuántos pedidos ha cancelado.
     */
    @Transactional
    public int caducarReservasVencidas(LocalDateTime limite, java.util.function.Predicate<Pedido> tienePagoEnCurso) {
        int cancelados = 0;
        List<Long> candidatos = pedidoRepository.findByEstadoInAndFechaPagoIsNullAndFechaEnvioFirmaBefore(
                List.of(EstadoPedido.PENDIENTE_FIRMA, EstadoPedido.FIRMADO), limite)
                .stream().map(Pedido::getId).toList();
        // Se recarga cada pedido: liberar la reserva limpia el contexto de Hibernate (clearAutomatically)
        // y los pedidos leídos antes quedarían desconectados, sin poder cargar sus líneas.
        for (Long id : candidatos) {
            Pedido pedido = buscarPedido(id);
            if (tienePagoEnCurso.test(pedido)) continue;
            cancelarYLiberar(pedido, "Reserva caducada: sin pago confirmado en el plazo establecido");
            pedidoRepository.save(pedido);
            cancelados++;
        }
        return cancelados;
    }

    private void cancelarYLiberar(Pedido pedido, String motivo) {
        if (pedido.getEstado().tieneReserva()) {
            for (LineaPedido linea : pedido.getLineas()) {
                productoRepository.liberarReserva(linea.getProducto().getId(), linea.getCantidad());
            }
        }
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedido.setFechaCancelacion(LocalDateTime.now());
        pedido.setMotivoCancelacion(motivo);
    }

    // ------------------------------------------------------------------
    // Pago confirmado (lo llama PagoService desde la notificación)
    // ------------------------------------------------------------------

    @Transactional
    public void marcarPagado(Long pedidoId) {
        Pedido pedido = buscarPedido(pedidoId);
        if (pedido.getFechaPago() != null) return;
        if (pedido.getEstado() != EstadoPedido.PENDIENTE_FIRMA && pedido.getEstado() != EstadoPedido.FIRMADO) {
            throw new ReglaNegocioException("El pedido no puede recibir un pago en este estado");
        }
        pedido.setFechaPago(LocalDateTime.now());
        // Pagado: si ya tiene la firma válida del veterinario asignado, queda listo para preparar;
        // si no, sigue esperando la firma (aparece en «Pagados pendientes de firma»).
        if (AutorizacionPedido.motivoFirmaNoValida(pedido) == null) {
            pedido.setEstado(EstadoPedido.LISTO_PREPARAR);
            pedido.setFechaListoPreparar(LocalDateTime.now());
        }
        programarSincronizacion(pedido);
        pedidoRepository.save(pedido);
    }

    // ------------------------------------------------------------------
    // Preparación y expedición (solo ADMIN)
    // ------------------------------------------------------------------

    @Transactional
    public PedidoResponse cambiarEstadoInterno(Long id, EstadoPedido destino, String username) {
        Pedido pedido = buscarPedido(id);
        comprobarTransicion(pedido, destino);
        Usuario usuario = sesionService.usuarioActual(username);
        AutorizacionPedido autorizacion = autorizacion(pedido);
        if (!autorizacion.firmaValida() || !autorizacion.pagoCompleto()) {
            throw new ReglaNegocioException("No se puede preparar ni expedir sin firma válida del veterinario asignado y pago completo confirmado"
                    + (autorizacion.motivoFirma() != null ? ": " + autorizacion.motivoFirma() : ""));
        }

        switch (destino) {
            case EN_PREPARACION -> pedido.setFechaInicioPreparacion(LocalDateTime.now());
            case PREPARADO -> {
                // Salida de expedición: si administración ya eligió una, se respeta; si no, la habitual
                // de la clínica. Nunca se elige una arbitraria.
                if (pedido.getDestino() == null) {
                    var habitual = pedido.getClinica().getDestinoHabitual();
                    if (habitual == null) {
                        throw new ReglaNegocioException("La clínica " + pedido.getClinica().getNombre()
                                + " no tiene salida habitual: asigna una salida al pedido antes de marcarlo como preparado");
                    }
                    pedido.asignarDestino(habitual, usuario, false);
                }
                completarBultosCalculados(pedido);
                pedido.setFechaPreparacion(LocalDateTime.now());
            }
            case EXPEDIDO -> {
                comprobarBultosCuadran(pedido);
                if (bultoRepository.findByPedidoId(id).stream().anyMatch(b -> b.getPalet() == null)) {
                    throw new ReglaNegocioException("Todos los bultos deben estar asignados a un palet con destino antes de expedir");
                }
                pedido.setFechaExpedicion(LocalDateTime.now());
                // Descuento de stock físico UNA SOLA VEZ (atomicidad en BD + transición de estado)
                for (LineaPedido linea : pedido.getLineas()) {
                    int filas = productoRepository.confirmarSalida(
                            linea.getProducto().getId(), linea.getCantidad());
                    if (filas == 0) {
                        throw new ReglaNegocioException("No se pudo descontar el stock de "
                                + linea.getProducto().getNombre());
                    }
                    movimientoRepository.save(new MovimientoStock(linea.getProducto(), usuario, pedido,
                            TipoMovimiento.SALIDA, linea.getCantidad(), "Expedición del pedido " + pedido.getId()));
                }
            }
            default -> throw new ReglaNegocioException("Transición no permitida");
        }
        pedido.setEstado(destino);
        programarSincronizacion(pedido);
        return respuesta(pedidoRepository.save(pedido));
    }

    /**
     * Al preparar: el peso tiene que estar completo y existir un registro de bulto por cada bulto
     * calculado. Crea solo los que falten, con identificador fijo VSM-{pedido}-B{n}, así repetir
     * no duplica nada. Si ya hay MÁS registros de los calculados no borra ninguno: lo rechaza
     * para que administración decida cuál sobra.
     */
    private void completarBultosCalculados(Pedido pedido) {
        PesoPedido peso = PesoPedido.de(pedido);
        if (!peso.completo()) {
            throw new ReglaNegocioException("No se puede preparar: falta el peso de "
                    + String.join(", ", peso.productosSinPeso()) + " (Administración → Productos)");
        }
        long registrados = bultoRepository.countByPedidoId(pedido.getId());
        if (registrados > peso.bultosCalculados()) {
            throw new ReglaNegocioException("El pedido tiene " + registrados + " bultos registrados y por su peso ("
                    + peso.pesoTotalKg() + " kg) le corresponden " + peso.bultosCalculados()
                    + ": elimina los sobrantes que no estén en un palet");
        }
        for (int n = 1; registrados < peso.bultosCalculados(); n++) {
            String identificador = "VSM-" + pedido.getId() + "-B" + n;
            if (!bultoRepository.existsByIdentificador(identificador)) {
                bultoRepository.save(new Bulto(identificador, pedido));
                registrados++;
            }
        }
    }

    /** Antes de expedir, los registros de bultos tienen que coincidir con los que exige el peso. */
    private void comprobarBultosCuadran(Pedido pedido) {
        PesoPedido peso = PesoPedido.de(pedido);
        long registrados = bultoRepository.countByPedidoId(pedido.getId());
        if (!peso.completo()) {
            throw new ReglaNegocioException("No se puede expedir: falta el peso de " + String.join(", ", peso.productosSinPeso()));
        }
        if (registrados != peso.bultosCalculados()) {
            throw new ReglaNegocioException("No se puede expedir: hay " + registrados + " bultos registrados y por su peso le corresponden "
                    + peso.bultosCalculados());
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Situación de firma y pago del pedido (ver AutorizacionPedido). */
    @Transactional(readOnly = true)
    public AutorizacionPedido autorizacion(Pedido pedido) {
        BigDecimal importeConfirmado = pagoRepository.findByPedidoIdOrderByFechaInicioDesc(pedido.getId()).stream()
                .filter(p -> p.getEstado() == EstadoPago.CONFIRMADO)
                .map(Pago::getImporte)
                .findFirst().orElse(null);
        return AutorizacionPedido.de(pedido, importeConfirmado);
    }

    private PedidoResponse respuesta(Pedido pedido) {
        return PedidoResponse.desde(pedido, autorizacion(pedido), bultoRepository.countByPedidoId(pedido.getId()));
    }

    private void programarSincronizacion(Pedido pedido) {
        pedido.setOdooSincronizacionPendiente(true);
        pedido.setOdooUltimoError(null);
        eventos.publishEvent(new PedidoActualizadoEvent(pedido.getId()));
    }

    private void comprobarAccesoAClinica(Pedido pedido, String username, boolean esAdmin) {
        if (esAdmin) {
            return;
        }
        Usuario usuario = sesionService.usuarioActual(username);
        if (!sesionService.puedeAccederAClinica(usuario, false, pedido.getClinica().getId())) {
            throw new AccesoDenegadoException("No tienes permiso sobre este pedido");
        }
    }

    private Long clinicaDelCliente(Usuario usuario) {
        return sesionService.clinicaDelUsuario(usuario)
                .orElseThrow(() -> new AccesoDenegadoException("Tu cuenta no está asociada a ninguna clínica"));
    }

    private void comprobarTransicion(Pedido pedido, EstadoPedido destino) {
        if (!pedido.getEstado().puedePasarA(destino)) {
            throw new ReglaNegocioException("No se puede pasar de " + pedido.getEstado() + " a " + destino);
        }
    }

    private Pedido buscarPedido(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido no existe"));
    }

    private Veterinario buscarVeterinarioPorUsername(String username) {
        return veterinarioRepository.findByUsuarioUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tu usuario no está asociado a ningún veterinario"));
    }
}
