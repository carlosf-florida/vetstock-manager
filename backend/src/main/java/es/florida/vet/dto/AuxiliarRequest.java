package es.florida.vet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuxiliarRequest(@NotBlank String username, @NotBlank String password,
                              @NotBlank String nombreCompleto, @NotNull Long clinicaId) {
}
