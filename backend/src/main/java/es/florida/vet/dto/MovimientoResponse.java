package es.florida.vet.dto;

import es.florida.vet.entity.MovimientoStock;

import java.time.LocalDateTime;

public record MovimientoResponse(Long id, Long productoId, String codigoProducto, String nombreProducto,
                                 String tipo, int cantidad, String motivo, String usuario, LocalDateTime fecha) {

    public static MovimientoResponse desde(MovimientoStock m) {
        return new MovimientoResponse(m.getId(), m.getProducto().getId(), m.getProducto().getCodigo(),
                m.getProducto().getNombre(), m.getTipo().name(), m.getCantidad(), m.getMotivo(),
                m.getUsuario().getUsername(), m.getFecha());
    }
}
