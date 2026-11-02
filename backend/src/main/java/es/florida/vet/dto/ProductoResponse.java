package es.florida.vet.dto;

import es.florida.vet.entity.Producto;

import java.math.BigDecimal;

public record ProductoResponse(Long id, String codigo, String nombre, String descripcion,
                               String categoria, BigDecimal precio,
                               int stockActual, int stockReservado, int stockDisponible,
                               int stockMinimo, boolean activo, boolean stockBajo) {

    public static ProductoResponse desde(Producto p) {
        return new ProductoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(),
                p.getCategoria().name(), p.getPrecio(),
                p.getStockActual(), p.getStockReservado(), p.getStockDisponible(),
                p.getStockMinimo(), p.isActivo(), p.isStockBajo());
    }
}
