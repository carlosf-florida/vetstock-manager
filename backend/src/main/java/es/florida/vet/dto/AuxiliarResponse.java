package es.florida.vet.dto;

import es.florida.vet.entity.Auxiliar;

public record AuxiliarResponse(Long id, Long usuarioId, String username, String nombreCompleto,
                               Long clinicaId, String clinicaNombre) {

    public static AuxiliarResponse desde(Auxiliar a) {
        return new AuxiliarResponse(a.getId(), a.getUsuario().getId(), a.getUsuario().getUsername(),
                a.getUsuario().getNombreCompleto(), a.getClinica().getId(), a.getClinica().getNombre());
    }
}
