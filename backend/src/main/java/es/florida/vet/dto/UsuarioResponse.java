package es.florida.vet.dto;

/** Perfil del usuario con sesión iniciada. */
public record UsuarioResponse(Long id, String username, String nombreCompleto, String rol,
                              Long clinicaId, String clinicaNombre) {
}
