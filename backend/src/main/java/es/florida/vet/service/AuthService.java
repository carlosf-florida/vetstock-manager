package es.florida.vet.service;

import es.florida.vet.dto.UsuarioResponse;
import es.florida.vet.entity.Auxiliar;
import es.florida.vet.entity.Usuario;
import es.florida.vet.entity.Veterinario;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.repository.AuxiliarRepository;
import es.florida.vet.repository.UsuarioRepository;
import es.florida.vet.repository.VeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final AuxiliarRepository auxiliarRepository;

    public AuthService(UsuarioRepository usuarioRepository,
                       VeterinarioRepository veterinarioRepository,
                       AuxiliarRepository auxiliarRepository) {
        this.usuarioRepository = usuarioRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.auxiliarRepository = auxiliarRepository;
    }

    /** Datos del usuario con la sesión iniciada, incluida su clínica si es cliente. */
    @Transactional(readOnly = true)
    public UsuarioResponse perfil(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe"));

        Long clinicaId = null;
        String clinicaNombre = null;

        Optional<Veterinario> vet = veterinarioRepository.findByUsuarioUsername(username);
        if (vet.isPresent()) {
            clinicaId = vet.get().getClinica().getId();
            clinicaNombre = vet.get().getClinica().getNombre();
        } else {
            Optional<Auxiliar> aux = auxiliarRepository.findByUsuarioUsername(username);
            if (aux.isPresent()) {
                clinicaId = aux.get().getClinica().getId();
                clinicaNombre = aux.get().getClinica().getNombre();
            }
        }
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(),
                usuario.getNombreCompleto(), usuario.getRol().name(), clinicaId, clinicaNombre);
    }
}
