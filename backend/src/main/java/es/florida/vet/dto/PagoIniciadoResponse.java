package es.florida.vet.dto;

import java.math.BigDecimal;

/** Respuesta al iniciar un pago. urlSimulada apunta a la pasarela de pruebas (SIMULADA). */
public record PagoIniciadoResponse(String referencia, BigDecimal importe, String moneda,
                                   String proveedor, String urlSimulada) {
}
