package es.florida.vet.dto;

import jakarta.validation.constraints.NotNull;

public record ResponsableClinicaRequest(@NotNull Long veterinarioId) {
}
