package es.florida.vet.dto;

import es.florida.vet.entity.Clinica;

public record ClinicaResponse(Long id, String nombre, String direccion, String telefono, boolean activa,
                              Long veterinarioResponsableId, String veterinarioResponsableNombre,
                              Long destinoHabitualId, String destinoHabitualEtiqueta) {
    public static ClinicaResponse desde(Clinica c) {
        var responsable = c.getVeterinarioResponsable();
        return new ClinicaResponse(c.getId(), c.getNombre(), c.getDireccion(), c.getTelefono(), c.isActiva(),
                responsable == null ? null : responsable.getId(),
                responsable == null ? null : responsable.getUsuario().getNombreCompleto(),
                c.getDestinoHabitual() == null ? null : c.getDestinoHabitual().getId(),
                c.getDestinoHabitual() == null ? null : c.getDestinoHabitual().getEtiqueta());
    }
}
