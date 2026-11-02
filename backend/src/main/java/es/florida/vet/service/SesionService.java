package es.florida.vet.service;

import es.florida.vet.entity.Auxiliar;
import es.florida.vet.entity.Usuario;
import es.florida.vet.entity.Veterinario;
import es.florida.vet.enums.Rol;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.repository.AuxiliarRepository;
import es.florida.vet.repository.UsuarioRepository;
import es.florida.vet.repository.VeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resuelve el contexto del usuario con sesión iniciada:
 * a qué clínica pertenece (si es cliente) y si puede acceder a los
 * datos de una clínica concreta. Todos los servicios lo usan para
 * comprobar permisos EN EL BACKEND, no solo ocultando botones.
 */
@Service
public class SesionService {

    private final UsuarioRepository usuarioRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final AuxiliarRepository auxiliarRepository;

    public SesionService(UsuarioRepository usuarioRepository,
                         VeterinarioRepository veterinarioRepository,
                         AuxiliarRepository auxiliarRepository) {
        this.usuarioRepository = usuarioRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.auxiliarRepository = auxiliarRepository;
    }

    @Transactional(readOnly = true)
    public Usuario usuarioActual(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe"));
    }

    /** Clínica del usuario cliente (veterinario o auxiliar). Vacío si es ADMIN. */
    @Transactional(readOnly = true)
    public Optional<Long> clinicaDelUsuario(Usuario usuario) {
        if (usuario.getRol() == Rol.VETERINARIO) {
            return veterinarioRepository.findByUsuarioUsername(usuario.getUsername())
                    .map(v -> v.getClinica().getId());
        }
        if (usuario.getRol() == Rol.AUXILIAR) {
            return auxiliarRepository.findByUsuarioUsername(usuario.getUsername())
                    .map(Auxiliar::getClinica)
                    .map(c -> c.getId());
        }
        return Optional.empty();
    }

    /** El ADMIN puede acceder a todo; los clientes solo a su clínica. */
    @Transactional(readOnly = true)
    public boolean puedeAccederAClinica(Usuario usuario, boolean esAdmin, Long clinicaId) {
        if (esAdmin) {
            return true;
        }
        return clinicaDelUsuario(usuario).map(id -> id.equals(clinicaId)).orElse(false);
    }
}
