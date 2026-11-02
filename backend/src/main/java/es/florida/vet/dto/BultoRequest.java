package es.florida.vet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BultoRequest(@NotBlank String identificador, @NotNull Long pedidoId) {
}
