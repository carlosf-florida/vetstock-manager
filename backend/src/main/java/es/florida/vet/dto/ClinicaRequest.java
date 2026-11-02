package es.florida.vet.dto;

import jakarta.validation.constraints.NotBlank;

public record ClinicaRequest(@NotBlank String nombre, String direccion, String telefono) {
}
