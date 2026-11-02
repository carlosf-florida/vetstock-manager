package es.florida.vet.service;

import es.florida.vet.dto.InformesResponse;
import es.florida.vet.dto.LineaPedidoResponse;
import es.florida.vet.dto.PedidoResponse;
import es.florida.vet.enums.EstadoPedido;
import es.florida.vet.repository.PedidoRepository;
import es.florida.vet.repository.ProductoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Informes para administración, calculados SIEMPRE a partir de los datos
 * reales de la base de datos (nada de números decorativos).
 * Las consultas son JPQL/SQL simples; los mismos datos servirían para
 * conectar Power BI más adelante.
 */
@Service
public class InformeService {

    @PersistenceContext
    private EntityManager em;

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;

    public InformeService(PedidoRepository pedidoRepository, ProductoRepository productoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
    }

    /** Estados cuyo pedido cuenta como "pedido real" para los informes. */
    private static final List<String> ESTADOS_VALIDOS = List.of(
            "PENDIENTE_FIRMA", "FIRMADO", "LISTO_PREPARAR", "EN_PREPARACION", "PREPARADO", "EXPEDIDO");

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<InformesResponse.ProductoTop> productosTop(int limite) {
        List<Object[]> filas = em.createQuery(
                "SELECT l.producto.codigo, l.producto.nombre, SUM(l.cantidad), SUM(l.cantidad * l.precioUnitario) " +
                "FROM LineaPedido l JOIN l.pedido p " +
                "WHERE p.estado <> :borrador AND p.estado <> :cancelado " +
                "GROUP BY l.producto.codigo, l.producto.nombre " +
                "ORDER BY SUM(l.cantidad) DESC")
                .setParameter("borrador", EstadoPedido.BORRADOR)
                .setParameter("cancelado", EstadoPedido.CANCELADO)
                .setMaxResults(limite)
                .getResultList();
        return filas.stream()
                .map(f -> new InformesResponse.ProductoTop((String) f[0], (String) f[1],
                        ((Number) f[2]).longValue(), (BigDecimal) f[3]))
                .toList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<InformesResponse.PedidosPorMes> pedidosPorMes() {
        List<Object[]> filas = em.createNativeQuery(
                "SELECT DATE_FORMAT(fecha_creacion, '%Y-%m') AS mes, COUNT(*) AS pedidos, " +
                "COALESCE((SELECT SUM(lp.cantidad * lp.precio_unitario) FROM lineas_pedido lp " +
                "  WHERE lp.pedido_id = p.id), 0) AS importe " +
                "FROM pedidos p WHERE p.estado NOT IN ('BORRADOR', 'CANCELADO') " +
                "GROUP BY DATE_FORMAT(fecha_creacion, '%Y-%m'), p.id " +
                "ORDER BY mes")
                .getResultList();
        // Agrupamos por mes en Java para sumar los importes de cada mes
        var porMes = new java.util.LinkedHashMap<String, InformesResponse.PedidosPorMes>();
        for (Object[] fila : filas) {
            String mes = (String) fila[0];
            long pedidos = ((Number) fila[1]).longValue();
            BigDecimal importe = new BigDecimal(fila[2].toString());
            InformesResponse.PedidosPorMes anterior = porMes.get(mes);
            long totalPedidos = (anterior == null ? 0 : anterior.pedidos()) + pedidos;
            BigDecimal totalImporte = (anterior == null ? BigDecimal.ZERO : anterior.importe()).add(importe);
            porMes.put(mes, new InformesResponse.PedidosPorMes(mes, totalPedidos, totalImporte));
        }
        return new ArrayList<>(porMes.values());
    }

    @Transactional(readOnly = true)
    public List<es.florida.vet.dto.ProductoResponse> stockBajo() {
        return productoRepository.findAllByOrderByNombreAsc().stream()
                .filter(p -> p.isActivo() && p.isStockBajo())
                .map(es.florida.vet.dto.ProductoResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public InformesResponse.Pendientes pendientes() {
        List<PedidoResponse> porFirma = pedidoRepository
                .findByEstadoOrderByFechaCreacionDesc(EstadoPedido.PENDIENTE_FIRMA)
                .stream().map(PedidoResponse::desde).toList();
        List<PedidoResponse> porPago = pedidoRepository
                .findAllByOrderByFechaCreacionDesc()
                .stream().filter(p -> p.getFechaPago() == null &&
                        (p.getEstado() == EstadoPedido.PENDIENTE_FIRMA || p.getEstado() == EstadoPedido.FIRMADO))
                .map(PedidoResponse::desde).toList();
        return new InformesResponse.Pendientes(porFirma.size(), porPago.size(), porFirma, porPago);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public InformesResponse.Tiempos tiempos() {
        List<Object[]> fila = em.createNativeQuery(
                "SELECT " +
                "AVG(TIMESTAMPDIFF(SECOND, p.fecha_creacion, p.fecha_firma)) / 3600.0, " +
                "AVG(ABS(TIMESTAMPDIFF(SECOND, p.fecha_firma, p.fecha_pago))) / 3600.0, " +
                "AVG(TIMESTAMPDIFF(SECOND, p.fecha_pago, p.fecha_expedicion)) / 3600.0, " +
                "AVG(TIMESTAMPDIFF(SECOND, p.fecha_creacion, p.fecha_expedicion)) / 3600.0 " +
                "FROM pedidos p WHERE p.fecha_expedicion IS NOT NULL")
                .getResultList();
        if (fila.isEmpty() || fila.get(0)[0] == null) {
            return new InformesResponse.Tiempos(null, null, null, null);
        }
        Object[] valores = fila.get(0);
        return new InformesResponse.Tiempos(
                valores[0] == null ? null : ((Number) valores[0]).doubleValue(),
                valores[1] == null ? null : ((Number) valores[1]).doubleValue(),
                valores[2] == null ? null : ((Number) valores[2]).doubleValue(),
                valores[3] == null ? null : ((Number) valores[3]).doubleValue());
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<InformesResponse.PorDestino> porDestino() {
        List<Object[]> filas = em.createNativeQuery(
                "SELECT d.nombre, " +
                "COUNT(DISTINCT p.id) AS pedidos, " +
                "COUNT(b.id) AS bultos " +
                "FROM destinos d " +
                "LEFT JOIN palets pa ON pa.destino_id = d.id " +
                "LEFT JOIN bultos b ON b.palet_id = pa.id " +
                "LEFT JOIN pedidos p ON p.id = b.pedido_id " +
                "GROUP BY d.id, d.nombre ORDER BY d.nombre")
                .getResultList();
        return filas.stream()
                .map(f -> new InformesResponse.PorDestino((String) f[0],
                        ((Number) f[1]).longValue(), ((Number) f[2]).longValue()))
                .toList();
    }
}
