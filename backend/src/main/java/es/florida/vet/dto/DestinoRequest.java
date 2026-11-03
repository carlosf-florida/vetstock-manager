package es.florida.vet.dto;

import jakarta.validation.constraints.NotBlank;

public record DestinoRequest(@jakarta.validation.constraints.Size(max = 10) String codigo, @NotBlank String nombre) {
}
