package es.florida.vet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LineaPedidoRequest(@NotNull Long productoId, @NotNull @Min(1) Integer cantidad) {
}
