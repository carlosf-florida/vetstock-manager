package es.florida.vet.service;

import es.florida.vet.dto.PedidoResponse;
import es.florida.vet.dto.ProductoResponse;
import es.florida.vet.entity.Pedido;
import es.florida.vet.enums.EstadoPedido;
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

    public DashboardService(PedidoRepository pedidoRepository,
                            ProductoRepository productoRepository,
                            SesionService sesionService) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.sesionService = sesionService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumen(String username, boolean esAdmin) {
        Map<String, Object> datos = new LinkedHashMap<>();

        if (esAdmin) {
            List<Pedido> ultimos = pedidoRepository.findAllByOrderByFechaCreacionDesc();
            datos.put("pendientesFirma", pedidoRepository.countByEstado(EstadoPedido.PENDIENTE_FIRMA));
            datos.put("pendientesPago", ultimos.stream().filter(DashboardService::pagoPendiente).count());
            datos.put("listosPreparar", pedidoRepository.countByEstado(EstadoPedido.LISTO_PREPARAR));
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
            datos.put("pendientesFirma", pedidos.stream().filter(p -> p.getEstado() == EstadoPedido.PENDIENTE_FIRMA).count());
            datos.put("pendientesPago", pedidos.stream().filter(DashboardService::pagoPendiente).count());
            datos.put("enCamino", pedidos.stream().filter(p -> p.getEstado() == EstadoPedido.PREPARADO
                    || p.getEstado() == EstadoPedido.EN_PREPARACION).count());
            datos.put("expedidos", pedidos.stream().filter(p -> p.getEstado() == EstadoPedido.EXPEDIDO).count());
            datos.put("ultimosPedidos", pedidos.stream().limit(5).map(PedidoResponse::desde).toList());
        }
        return datos;
    }

    private static boolean pagoPendiente(Pedido p) {
        return p.getFechaPago() == null && (p.getEstado() == EstadoPedido.PENDIENTE_FIRMA
                || p.getEstado() == EstadoPedido.FIRMADO);
    }
}
