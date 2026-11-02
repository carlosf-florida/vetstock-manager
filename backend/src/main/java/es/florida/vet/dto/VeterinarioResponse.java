package es.florida.vet.dto;

import es.florida.vet.entity.Veterinario;

public record VeterinarioResponse(Long id, Long usuarioId, String username, String nombreCompleto,
                                  Long clinicaId, String clinicaNombre, String numeroColegiado, String telefono) {

    public static VeterinarioResponse desde(Veterinario v) {
        return new VeterinarioResponse(v.getId(), v.getUsuario().getId(), v.getUsuario().getUsername(),
                v.getUsuario().getNombreCompleto(), v.getClinica().getId(), v.getClinica().getNombre(),
                v.getNumeroColegiado(), v.getTelefono());
    }
}
