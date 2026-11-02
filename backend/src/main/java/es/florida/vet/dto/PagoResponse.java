package es.florida.vet.dto;

import es.florida.vet.entity.Pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoResponse(Long id, Long pedidoId, String referencia, String proveedor, String estado,
                           BigDecimal importe, String moneda, int intentos,
                           LocalDateTime fechaInicio, LocalDateTime fechaResultado) {

    public static PagoResponse desde(Pago p) {
        return new PagoResponse(p.getId(), p.getPedido().getId(), p.getReferencia(), p.getProveedor(),
                p.getEstado().name(), p.getImporte(), p.getMoneda(), p.getIntentos(),
                p.getFechaInicio(), p.getFechaResultado());
    }
}
