package es.florida.vet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LineaPedidoRequest(@NotNull Long productoId, @NotNull @Min(1) @jakarta.validation.constraints.Max(value = 9999, message = "La cantidad máxima por línea es 9999") Integer cantidad) {
}
