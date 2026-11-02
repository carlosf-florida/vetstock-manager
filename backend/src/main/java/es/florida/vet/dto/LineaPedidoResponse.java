package es.florida.vet.dto;

import es.florida.vet.entity.LineaPedido;

import java.math.BigDecimal;

public record LineaPedidoResponse(Long id, Long productoId, String codigoProducto, String nombreProducto,
                                  int cantidad, BigDecimal precioUnitario, BigDecimal importe) {

    public static LineaPedidoResponse desde(LineaPedido l) {
        return new LineaPedidoResponse(l.getId(), l.getProducto().getId(), l.getProducto().getCodigo(),
                l.getProducto().getNombre(), l.getCantidad(), l.getPrecioUnitario(), l.getImporte());
    }
}
