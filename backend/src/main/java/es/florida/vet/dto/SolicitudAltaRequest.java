package es.florida.vet.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Formulario público. El teléfono y el código postal solo comprueban caracteres: el proyecto no
 * limita el país, así que no se impone ningún formato fiscal ni profesional concreto.
 * sitioWeb es un campo trampa oculto: una persona no lo rellena.
 */
public record SolicitudAltaRequest(
        @NotBlank(message = "Escribe el nombre de la clínica") @Size(max = 100, message = "El nombre admite 100 caracteres") String clinicaNombre,
        @Size(max = 120, message = "La razón social admite 120 caracteres") String razonSocial,
        @Size(max = 20, message = "El identificador fiscal admite 20 caracteres") String identificadorFiscal,
        @NotBlank(message = "Escribe el email de la clínica") @Email(message = "El email de la clínica no es válido") @Size(max = 120, message = "El email admite 120 caracteres") String email,
        @NotBlank(message = "Escribe el teléfono") @Pattern(regexp = "^[+0-9 ()-]{6,20}$", message = "El teléfono no es válido") String telefono,
        @NotBlank(message = "Escribe la dirección") @Size(max = 70, message = "La dirección admite 70 caracteres") String direccion,
        @NotBlank(message = "Escribe el código postal") @Pattern(regexp = "^[A-Za-z0-9 -]{3,10}$", message = "El código postal no es válido") String codigoPostal,
        @NotBlank(message = "Escribe la localidad") @Size(max = 30, message = "La localidad admite 30 caracteres") String localidad,
        @NotBlank(message = "Escribe la provincia o región") @Size(max = 30, message = "La provincia admite 30 caracteres") String provincia,
        @NotBlank(message = "Escribe el país") @Size(max = 40, message = "El país admite 40 caracteres") String pais,
        @NotBlank(message = "Escribe el nombre del veterinario") @Size(max = 100, message = "El nombre admite 100 caracteres") String vetNombre,
        @NotBlank(message = "Escribe el número de colegiado") @Size(max = 20, message = "El número de colegiado admite 20 caracteres") String vetColegiado,
        @NotBlank(message = "Indica el colegio o la provincia del número de colegiado") @Size(max = 60, message = "El colegio admite 60 caracteres") String vetColegio,
        @NotBlank(message = "Escribe el email del veterinario") @Email(message = "El email del veterinario no es válido") @Size(max = 120, message = "El email admite 120 caracteres") String vetEmail,
        @Pattern(regexp = "^$|^[+0-9 ()-]{6,20}$", message = "El teléfono del veterinario no es válido") String vetTelefono,
        @Size(max = 500, message = "Las observaciones admiten 500 caracteres") String observaciones,
        @NotNull(message = "Confirma que has leído la información de privacidad") @AssertTrue(message = "Confirma que has leído la información de privacidad") Boolean privacidadLeida,
        String sitioWeb) {
}
