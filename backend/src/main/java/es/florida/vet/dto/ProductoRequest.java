package es.florida.vet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductoRequest(
        @NotBlank String codigo,
        @NotBlank String nombre,
        String descripcion,
        @NotNull String categoria,          // se convierte a enum en el servicio
        @NotNull @DecimalMin(value = "0.0") BigDecimal precio,
        // kg por unidad, opcional (null = desconocido); como mucho 3 decimales (gramos)
        @DecimalMin(value = "0.001", message = "El peso debe ser mayor que 0")
        @Digits(integer = 7, fraction = 3, message = "El peso admite como mucho 3 decimales") BigDecimal pesoUnitarioKg,
        @NotNull @Min(0) Integer stockInicial,
        @NotNull @Min(0) Integer stockMinimo) {
}
