package es.florida.vet.dto;

import jakarta.validation.constraints.NotBlank;

public record DestinoRequest(@NotBlank String nombre) {
}
