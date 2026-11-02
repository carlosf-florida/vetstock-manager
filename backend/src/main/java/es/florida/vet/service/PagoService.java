package es.florida.vet.service;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Pagos con un ADAPTADOR SIMULADO (entorno de pruebas).
 *
 * IMPORTANTE: este proveedor NO es una integración bancaria real.
 * En producción se sustituiría por Redsys u otro proveedor con
 * credenciales contratadas; el flujo sería el mismo:
 *
 *   1) iniciar()      -> crea el intento de pago con referencia única
 *   2) el proveedor   -> llama a notificacion() (webhook autenticado)
 *   3) notificacion() -> valida referencia, importe y moneda, y es
 *      IDEMPOTENTE: las notificaciones repetidas no duplican efectos
 *   4) CONFIRMADO registra el pago y solicita Odoo; LISTO_PREPARAR exige además firma
 *
 * El backend NUNCA se fía de la pantalla de éxito del navegador y no
 * almacena datos sensibles de pago (números de tarjeta, etc.).
 */
@Service
public class PagoService {

    private final PagoRepository pagoRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final SesionService sesionService;

    @Value("${vetstock.pagos.token-notificacion}")
    private String tokenNotificacionConfigurado;

    public PagoService(PagoRepository pagoRepository,
                       PedidoRepository pedidoRepository,
                       PedidoService pedidoService,
                       SesionService sesionService) {
        this.pagoRepository = pagoRepository;
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.sesionService = sesionService;
    }

    /** Paga un pedido confirmado, firmado o pendiente de firma. Reintento tras fallo con referencia nueva. */
    @Transactional
    public PagoIniciadoResponse iniciar(Long pedidoId, String username, boolean esAdmin) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido no existe"));
        comprobarAccesoAClinica(pedido, username, esAdmin);

        if (pedido.getFechaPago() != null) {
            throw new ReglaNegocioException("El pedido ya está pagado");
        }
        if (pedido.getEstado() != EstadoPedido.FIRMADO && pedido.getEstado() != EstadoPedido.PENDIENTE_FIRMA) {
            throw new ReglaNegocioException("Confirma primero el pedido; puedes firmarlo ahora o más tarde");
        }
        boolean yaHayPendiente = pagoRepository.findByPedidoIdOrderByFechaInicioDesc(pedidoId).stream()
                .anyMatch(p -> p.getEstado() == EstadoPago.PENDIENTE);
        if (yaHayPendiente) {
            throw new ReglaNegocioException("Ya existe un pago en curso para este pedido");
        }

        // El importe SIEMPRE lo calcula el servidor a partir de las líneas
        BigDecimal importe = pedido.getTotal();
        String referencia = "SIM-" + UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        Pago pago = new Pago(pedido, importe, referencia, token);
        pagoRepository.save(pago);

        // URL de la pasarela SIMULADA que maneja el frontend
        return new PagoIniciadoResponse(referencia, importe, "EUR", "SIMULADO",
                "/pago-simulado/" + referencia);
    }

    /**
     * Procesa la SIMULACIÓN de la página del banco: llama internamente a la
     * notificación como haría el proveedor real con su webhook.
     */
    @Transactional
    public PagoResponse simuladoProcesar(String referencia, String resultado) {
        Pago pago = pagoRepository.findByReferencia(referencia)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pago no existe"));
        NotificacionPagoRequest notificacion = new NotificacionPagoRequest(
                referencia, pago.getImporte(), pago.getMoneda(), resultado);
        // El "proveedor simulado" llama a la notificación con SU token configurado,
        // igual que haría un proveedor real con su secreto del webhook
        return notificacion(notificacion, tokenNotificacionConfigurado);
    }

    /**
     * Endpoint que en producción llamaría el proveedor (webhook).
     * Autenticación por token, validación de importe/moneda e idempotencia.
     */
    @Transactional
    public PagoResponse notificacion(NotificacionPagoRequest datos, String tokenRecibido) {
        if (tokenRecibido == null || !tokenRecibido.equals(tokenNotificacionConfigurado)) {
            throw new AccesoDenegadoException("Notificación de pago no autorizada");
        }
        Pago pago = pagoRepository.findByReferencia(datos.referencia())
                .orElseThrow(() -> new RecursoNoEncontradoException("La referencia de pago no existe"));

        pago.incrementarIntentos();

        // IDEMPOTENCIA: si ya estaba resuelto, no se repite ningún efecto
        if (pago.getEstado() != EstadoPago.PENDIENTE) {
            return PagoResponse.desde(pago);
        }

        // Validación del pedido, importe y moneda
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

        if ("CONFIRMADO".equals(datos.resultado())) {
            marcarResultado(pago, EstadoPago.CONFIRMADO);
            // Registrar pago siempre. La preparación exige además una firma;
            // la exportación a Odoo se solicita tras el commit del pago.
            pedidoService.marcarPagado(pedido.getId());
        } else if ("FALLADO".equals(datos.resultado())) {
            marcarResultado(pago, EstadoPago.FALLADO);
        } else {
            throw new ReglaNegocioException("El resultado de la notificación no es válido");
        }
        return PagoResponse.desde(pago);
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
