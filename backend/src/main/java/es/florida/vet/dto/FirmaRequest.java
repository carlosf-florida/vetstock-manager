package es.florida.vet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Firma simple propia. El colegiado opcional identifica al responsable; no autentica. */
public record FirmaRequest(@NotNull String tipoFirma,
                           @NotBlank String imagenBase64,
                           String nombreArchivo,
                           @NotNull Boolean confirmacion,
                           @Size(max = 20) String numeroColegiado) {
    public FirmaRequest(String tipoFirma, String imagenBase64, String nombreArchivo, Boolean confirmacion) {
        this(tipoFirma, imagenBase64, nombreArchivo, confirmacion, null);
    }
}
