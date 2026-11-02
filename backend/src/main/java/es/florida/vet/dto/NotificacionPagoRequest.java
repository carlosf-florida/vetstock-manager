package es.florida.vet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Notificación que envía el proveedor de pagos al backend (webhook).
 * En el MVP la genera el adaptador SIMULADO. El backend valida referencia,
 * importe y moneda, y nunca se fía de la pantalla de éxito del navegador.
 */
public record NotificacionPagoRequest(@NotBlank String referencia,
                                      @NotNull BigDecimal importe,
                                      @NotBlank String moneda,
                                      @NotBlank String resultado) {   // CONFIRMADO | FALLADO
}
