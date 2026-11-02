package es.florida.vet.service;

import es.florida.vet.dto.PedidoResponse;
import es.florida.vet.dto.ProductoResponse;
import es.florida.vet.entity.Pedido;
import es.florida.vet.enums.EstadoPedido;
import es.florida.vet.repository.PagoRepository;
import es.florida.vet.repository.PedidoRepository;
import es.florida.vet.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resumen inicial para la pantalla de inicio, distinto según el rol. */
@Service
public class DashboardService {

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final SesionService sesionService;
    private final PagoRepository pagoRepository;

    public DashboardService(PedidoRepository pedidoRepository,
                            ProductoRepository productoRepository,
                            SesionService sesionService,
                            PagoRepository pagoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.sesionService = sesionService;
        this.pagoRepository = pagoRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumen(String username, boolean esAdmin) {
        Map<String, Object> datos = new LinkedHashMap<>();

        if (esAdmin) {
            List<Pedido> ultimos = pedidoRepository.findAllByOrderByFechaCreacionDesc();
            datos.put("pendientesFirma", ultimos.stream().filter(DashboardService::necesitaFirma).count());
            datos.put("pendientesPago", ultimos.stream().filter(DashboardService::pagoPendiente).count());
            // Listo de verdad = firma válida y pago completo (mismo cálculo que la lista de pedidos)
            Map<Long, java.math.BigDecimal> confirmados = new java.util.HashMap<>();
            pagoRepository.findByEstado(es.florida.vet.enums.EstadoPago.CONFIRMADO)
                    .forEach(pago -> confirmados.put(pago.getPedido().getId(), pago.getImporte()));
            datos.put("listosPreparar", ultimos.stream()
                    .filter(p -> AutorizacionPedido.de(p, confirmados.get(p.getId())).listoParaPreparar()).count());
            datos.put("enPreparacion", pedidoRepository.countByEstado(EstadoPedido.EN_PREPARACION));
            datos.put("preparados", pedidoRepository.countByEstado(EstadoPedido.PREPARADO));
            datos.put("expedidos", pedidoRepository.countByEstado(EstadoPedido.EXPEDIDO));
            long stockBajo = productoRepository.findAllByOrderByNombreAsc().stream()
                    .filter(p -> p.isActivo() && p.isStockBajo()).count();
            datos.put("productosStockBajo", stockBajo);
            datos.put("ultimosPedidos", ultimos.stream().limit(5).map(PedidoResponse::desde).toList());
        } else {
            var usuario = sesionService.usuarioActual(username);
            Long clinica = sesionService.clinicaDelUsuario(usuario).orElse(null);
            List<Pedido> pedidos = (clinica == null) ? List.of()
                    : pedidoRepository.findByClinicaIdOrderByFechaCreacionDesc(clinica);
            datos.put("pendientesFirma", pedidos.stream().filter(DashboardService::necesitaFirma).count());
            datos.put("pendientesPago", pedidos.stream().filter(DashboardService::pagoPendiente).count());
            datos.put("enCamino", pedidos.stream().filter(p -> p.getEstado() == EstadoPedido.PREPARADO
                    || p.getEstado() == EstadoPedido.EN_PREPARACION).count());
            datos.put("expedidos", pedidos.stream().filter(p -> p.getEstado() == EstadoPedido.EXPEDIDO).count());
            datos.put("ultimosPedidos", pedidos.stream().limit(5).map(PedidoResponse::desde).toList());
        }
        return datos;
    }

    /**
     * Necesita firma = aún en el circuito de firma y sin firma VÁLIDA del veterinario asignado
     * (incluye pedidos con una firma de otra persona), lo haya creado quien lo haya creado.
     */
    private static boolean necesitaFirma(Pedido p) {
        return (p.getEstado() == EstadoPedido.PENDIENTE_FIRMA || p.getEstado() == EstadoPedido.FIRMADO
                || p.getEstado() == EstadoPedido.LISTO_PREPARAR)
                && AutorizacionPedido.motivoFirmaNoValida(p) != null;
    }

    /** Pendiente de pago = enviado a firma (firmado o no) y sin pago confirmado. */
    private static boolean pagoPendiente(Pedido p) {
        return p.getFechaPago() == null && (p.getEstado() == EstadoPedido.PENDIENTE_FIRMA
                || p.getEstado() == EstadoPedido.FIRMADO);
    }
}
