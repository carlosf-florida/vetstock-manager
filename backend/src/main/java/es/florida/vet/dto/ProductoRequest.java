package es.florida.vet.dto;

import jakarta.validation.constraints.DecimalMin;
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
        @NotNull @Min(0) Integer stockInicial,
        @NotNull @Min(0) Integer stockMinimo) {
}
