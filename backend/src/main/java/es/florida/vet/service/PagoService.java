package es.florida.vet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.florida.vet.dto.NotificacionPagoRequest;
import es.florida.vet.dto.PagoIniciadoResponse;
import es.florida.vet.dto.PagoResponse;
import es.florida.vet.entity.Pago;
import es.florida.vet.entity.Pedido;
import es.florida.vet.enums.EstadoPago;
import es.florida.vet.enums.EstadoPedido;
import es.florida.vet.exception.AccesoDenegadoException;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.repository.PagoRepository;
import es.florida.vet.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Pagos con un ADAPTADOR DE DEMOSTRACIÓN (entorno de pruebas).
 *
 * IMPORTANTE: este proveedor NO es una integración bancaria real y no mueve
 * dinero. Imita el funcionamiento de pasarelas como Redsys:
 *
 *   1) iniciar()      -> pedido enviado a firma (firmado o aún sin firmar); crea el intento de pago
 *                        con referencia única y el importe calculado en el servidor
 *   2) el proveedor   -> llama a notificacion() (webhook) con el cuerpo firmado
 *                        con HMAC-SHA256 y una clave secreta compartida
 *   3) notificacion() -> verifica la firma, bloquea el pago, valida pedido,
 *                        importe y moneda, y es IDEMPOTENTE
 *   4) CONFIRMADO     -> pedido pagado; pasa a LISTO_PREPARAR solo si ya tiene firma válida
 *
 * El backend NUNCA se fía de la pantalla de éxito del navegador y no
 * almacena datos sensibles de pago (números de tarjeta, etc.).
 */
@Service
public class PagoService {

    /** Cabecera HTTP donde el proveedor envía la firma HMAC (Base64) del cuerpo. */
    public static final String CABECERA_FIRMA = "X-Firma-Notificacion";

    private final PagoRepository pagoRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final SesionService sesionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${vetstock.pagos.secreto-webhook}")
    private String secretoWebhook;

    @Value("${vetstock.pagos.simulado-activo:true}")
    private boolean simuladoActivo = true;

    public PagoService(PagoRepository pagoRepository,
                       PedidoRepository pedidoRepository,
                       PedidoService pedidoService,
                       SesionService sesionService) {
        this.pagoRepository = pagoRepository;
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.sesionService = sesionService;
    }

    /** Inicia el pago de un pedido enviado a firma (firmado o no). Tras un fallo se reintenta con referencia nueva. */
    @Transactional
    public PagoIniciadoResponse iniciar(Long pedidoId, String username, boolean esAdmin) {
        if (esAdmin) {
            // Administración puede ayudar con el pedido, pero el pago lo hace la clínica
            throw new AccesoDenegadoException("El pago lo realiza la clínica; administración no puede pagar en su nombre");
        }
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido no existe"));
        comprobarAccesoAClinica(pedido, username, false);

        if (pedido.getFechaPago() != null) {
            throw new ReglaNegocioException("El pedido ya está pagado");
        }
        if (pedido.getEstado() != EstadoPedido.PENDIENTE_FIRMA && pedido.getEstado() != EstadoPedido.FIRMADO) {
            throw new ReglaNegocioException("Solo se puede pagar un pedido enviado a firma que aún no está pagado");
        }
        // Si ya hay un pago en curso se devuelve el mismo: así no se crean dos cobros
        Pago pendiente = pagoRepository.findByPedidoIdOrderByFechaInicioDesc(pedidoId).stream()
                .filter(p -> p.getEstado() == EstadoPago.PENDIENTE)
                .findFirst().orElse(null);
        if (pendiente != null) {
            return new PagoIniciadoResponse(pendiente.getReferencia(), pendiente.getImporte(),
                    pendiente.getMoneda(), "SIMULADO", "/pago-simulado/" + pendiente.getReferencia());
        }

        // El importe SIEMPRE lo calcula el servidor a partir de las líneas
        BigDecimal importe = pedido.getTotal();
        String referencia = "SIM-" + UUID.randomUUID();
        Pago pago = new Pago(pedido, importe, referencia, UUID.randomUUID().toString());
        pagoRepository.save(pago);

        // URL de la pasarela SIMULADA que maneja el frontend
        return new PagoIniciadoResponse(referencia, importe, "EUR", "SIMULADO",
                "/pago-simulado/" + referencia);
    }

    /**
     * Página de la pasarela SIMULADA: el usuario elige el resultado y el
     * "proveedor" envía su notificación firmada al webhook, igual que haría uno real.
     */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public PagoResponse simuladoProcesar(String referencia, String resultado, String username, boolean esAdmin) {
        if (!simuladoActivo) {
            throw new AccesoDenegadoException("La pasarela de pago simulada está desactivada");
        }
        if (esAdmin) {
            throw new AccesoDenegadoException("Solo la clínica puede completar su pago");
        }
        Pago pago = pagoRepository.findByReferencia(referencia)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pago no existe"));
        comprobarAccesoAClinica(pago.getPedido(), username, false);
        String cuerpo = aJson(new NotificacionPagoRequest(referencia, pago.getImporte(), pago.getMoneda(), resultado));
        return notificacion(cuerpo, firmarCuerpo(cuerpo));
    }

    /**
     * Webhook del proveedor. Autenticado con HMAC-SHA256 del cuerpo exacto
     * recibido; valida importe y moneda y es idempotente.
     * noRollbackFor: si la notificación no cuadra, el pago queda FALLADO aunque se responda con error.
     */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public PagoResponse notificacion(String cuerpo, String firmaRecibida) {
        if (cuerpo == null || !firmaValida(cuerpo, firmaRecibida)) {
            throw new AccesoDenegadoException("Notificación de pago no autorizada");
        }
        NotificacionPagoRequest datos = leerNotificacion(cuerpo);
        Pago pago = pagoRepository.bloquearPorReferencia(datos.referencia())
                .orElseThrow(() -> new RecursoNoEncontradoException("La referencia de pago no existe"));

        pago.incrementarIntentos();

        // IDEMPOTENCIA: si ya estaba resuelto, no se repite ningún efecto
        if (pago.getEstado() != EstadoPago.PENDIENTE) {
            return PagoResponse.desde(pagoRepository.save(pago));
        }

        Pedido pedido = pago.getPedido();
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            marcarResultado(pago, EstadoPago.FALLADO);
            return PagoResponse.desde(pago);
        }
        boolean importeCorrecto = pago.getImporte().compareTo(datos.importe()) == 0
                && pago.getImporte().compareTo(pedido.getTotal()) == 0;
        boolean monedaCorrecta = pago.getMoneda().equals(datos.moneda());
        if (!importeCorrecto || !monedaCorrecta) {
            marcarResultado(pago, EstadoPago.FALLADO);
            throw new ReglaNegocioException("La notificación no coincide con el importe o la moneda del pedido");
        }

        switch (datos.resultado()) {
            case "CONFIRMADO" -> {
                // Se comprueba aquí y no dentro de marcarPagado para no dejar la transacción marcada para rollback
                if (pedido.getEstado() != EstadoPedido.PENDIENTE_FIRMA && pedido.getEstado() != EstadoPedido.FIRMADO) {
                    marcarResultado(pago, EstadoPago.FALLADO);
                    throw new ReglaNegocioException("El pedido no admite el pago en su estado actual");
                }
                marcarResultado(pago, EstadoPago.CONFIRMADO);
                pedidoService.marcarPagado(pedido.getId());
            }
            case "FALLADO" -> marcarResultado(pago, EstadoPago.FALLADO);
            default -> throw new ReglaNegocioException("El resultado de la notificación no es válido");
        }
        return PagoResponse.desde(pago);
    }

    /** Los pagos que llevan demasiado tiempo PENDIENTE se dan por abandonados (la sesión de pago caduca). */
    @Transactional
    public int caducarPagosAbandonados(LocalDateTime limite) {
        List<Pago> abandonados = pagoRepository.findByEstadoAndFechaInicioBefore(EstadoPago.PENDIENTE, limite);
        abandonados.forEach(p -> marcarResultado(p, EstadoPago.FALLADO));
        return abandonados.size();
    }

    @Transactional(readOnly = true)
    public boolean tienePagoEnCurso(Long pedidoId, LocalDateTime desde) {
        return pagoRepository.existsByPedidoIdAndEstadoAndFechaInicioAfter(pedidoId, EstadoPago.PENDIENTE, desde);
    }

    @Transactional(readOnly = true)
    public List<PagoResponse> listar(Long pedidoId, String estado, String username, boolean esAdmin) {
        List<Pago> pagos;
        if (pedidoId != null) {
            Pedido pedido = pedidoRepository.findById(pedidoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("El pedido no existe"));
            comprobarAccesoAClinica(pedido, username, esAdmin);
            pagos = pagoRepository.findByPedidoIdOrderByFechaInicioDesc(pedidoId);
        } else if (esAdmin) {
            pagos = (estado == null)
                    ? pagoRepository.findAll()
                    : pagoRepository.findByEstado(EstadoPago.valueOf(estado));
        } else {
            throw new AccesoDenegadoException("Indica un pedido para consultar su pago");
        }
        return pagos.stream().map(PagoResponse::desde).toList();
    }

    // ------------------------------------------------------------------
    // Firma HMAC del webhook
    // ------------------------------------------------------------------

    /** Firma que calcularía el proveedor: Base64(HMAC-SHA256(secreto, cuerpo)). */
    public String firmarCuerpo(String cuerpo) {
        return FirmaHmac.calcular(secretoWebhook, cuerpo);
    }

    private boolean firmaValida(String cuerpo, String firmaRecibida) {
        if (firmaRecibida == null || firmaRecibida.isBlank()) return false;
        byte[] esperada = firmarCuerpo(cuerpo).getBytes(StandardCharsets.UTF_8);
        // Comparación en tiempo constante para no dar pistas sobre la firma correcta
        return MessageDigest.isEqual(esperada, firmaRecibida.trim().getBytes(StandardCharsets.UTF_8));
    }

    private NotificacionPagoRequest leerNotificacion(String cuerpo) {
        NotificacionPagoRequest datos;
        try {
            datos = objectMapper.readValue(cuerpo, NotificacionPagoRequest.class);
        } catch (JsonProcessingException e) {
            throw new ReglaNegocioException("La notificación de pago no tiene un formato válido");
        }
        if (datos.referencia() == null || datos.importe() == null
                || datos.moneda() == null || datos.resultado() == null) {
            throw new ReglaNegocioException("La notificación de pago está incompleta");
        }
        return datos;
    }

    private String aJson(NotificacionPagoRequest datos) {
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo generar la notificación", e);
        }
    }

    private void marcarResultado(Pago pago, EstadoPago estado) {
        pago.setEstado(estado);
        pago.setFechaResultado(LocalDateTime.now());
        pagoRepository.save(pago);
    }

    private void comprobarAccesoAClinica(Pedido pedido, String username, boolean esAdmin) {
        if (esAdmin) {
            return;
        }
        var usuario = sesionService.usuarioActual(username);
        if (!sesionService.puedeAccederAClinica(usuario, false, pedido.getClinica().getId())) {
            throw new AccesoDenegadoException("No tienes permiso sobre este pedido");
        }
    }
}
