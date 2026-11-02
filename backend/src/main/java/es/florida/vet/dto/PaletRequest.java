package es.florida.vet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaletRequest(@NotBlank String identificador, @NotNull Long destinoId) {
}
