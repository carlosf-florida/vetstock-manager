package es.florida.vet.service;

import es.florida.vet.dto.AuxiliarRequest;
import es.florida.vet.dto.AuxiliarResponse;
import es.florida.vet.dto.ClinicaRequest;
import es.florida.vet.dto.ClinicaResponse;
import es.florida.vet.dto.VeterinarioRequest;
import es.florida.vet.dto.VeterinarioResponse;
import es.florida.vet.entity.Auxiliar;
import es.florida.vet.entity.Clinica;
import es.florida.vet.entity.Usuario;
import es.florida.vet.entity.Veterinario;
import es.florida.vet.enums.Rol;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.repository.AuxiliarRepository;
import es.florida.vet.repository.ClinicaRepository;
import es.florida.vet.repository.UsuarioRepository;
import es.florida.vet.repository.VeterinarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestión de clínicas y de sus cuentas (veterinarios y auxiliares).
 * Las contraseñas se guardan cifradas con BCrypt, nunca en texto plano.
 */
@Service
public class ClinicaService {

    private final ClinicaRepository clinicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final AuxiliarRepository auxiliarRepository;
    private final PasswordEncoder passwordEncoder;
    private final SesionService sesionService;

    private final es.florida.vet.repository.DestinoRepository destinoRepository;

    public ClinicaService(ClinicaRepository clinicaRepository, UsuarioRepository usuarioRepository,
                          VeterinarioRepository veterinarioRepository, AuxiliarRepository auxiliarRepository,
                          PasswordEncoder passwordEncoder, SesionService sesionService,
                          es.florida.vet.repository.DestinoRepository destinoRepository) {
        this.destinoRepository = destinoRepository;
        this.clinicaRepository = clinicaRepository;
        this.usuarioRepository = usuarioRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.auxiliarRepository = auxiliarRepository;
        this.passwordEncoder = passwordEncoder;
        this.sesionService = sesionService;
    }

    // ----- Clínicas -----

    @Transactional(readOnly = true)
    public List<ClinicaResponse> listarClinicas() {
        return clinicaRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Clinica::getNombre))
                .map(ClinicaResponse::desde).toList();
    }

    @Transactional
    public ClinicaResponse crearClinica(ClinicaRequest datos) {
        return ClinicaResponse.desde(registrarClinica(datos.nombre(), datos.direccion(), datos.telefono()));
    }

    /** Misma regla para el alta manual y para el alta desde una solicitud. */
    @Transactional
    public Clinica registrarClinica(String nombre, String direccion, String telefono) {
        if (clinicaRepository.findFirstByNombreIgnoreCase(nombre).isPresent()) {
            throw new ReglaNegocioException("Ya existe una clínica con ese nombre");
        }
        return clinicaRepository.save(new Clinica(nombre, direccion, telefono));
    }

    @Transactional
    public ClinicaResponse asignarResponsable(Long clinicaId, Long veterinarioId) {
        Clinica clinica = clinicaRepository.findById(clinicaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La clínica no existe"));
        Veterinario veterinario = veterinarioRepository.findById(veterinarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El veterinario no existe"));
        if (!veterinario.getClinica().getId().equals(clinicaId)) {
            throw new ReglaNegocioException("El veterinario debe pertenecer a la clínica seleccionada");
        }
        clinica.setVeterinarioResponsable(veterinario);
        return ClinicaResponse.desde(clinicaRepository.save(clinica));
    }

    /**
     * Salida de expedición habitual de la clínica (null = quitarla). Solo afecta a los pedidos que
     * se preparen a partir de ahora: los ya preparados o expedidos conservan la suya.
     */
    @Transactional
    public ClinicaResponse asignarSalidaHabitual(Long clinicaId, Long destinoId) {
        Clinica clinica = clinicaRepository.findById(clinicaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La clínica no existe"));
        clinica.setDestinoHabitual(destinoId == null ? null : destinoRepository.findById(destinoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La salida no existe")));
        return ClinicaResponse.desde(clinicaRepository.save(clinica));
    }

    // ----- Veterinarios -----

    @Transactional(readOnly = true)
    public List<VeterinarioResponse> listarVeterinarios() {
        return veterinarioRepository.findAllByOrderByIdAsc().stream()
                .map(VeterinarioResponse::desde).toList();
    }

    /** Para el portal cliente solo se exponen los veterinarios de su clínica. */
    @Transactional(readOnly = true)
    public List<VeterinarioResponse> listarVeterinariosPara(String username) {
        Usuario usuario = sesionService.usuarioActual(username);
        List<Veterinario> resultado = usuario.getRol() == Rol.ADMIN
                ? veterinarioRepository.findAllByOrderByIdAsc()
                : sesionService.clinicaDelUsuario(usuario)
                    .map(veterinarioRepository::findByClinicaId)
                    .orElse(List.of());
        return resultado.stream().map(VeterinarioResponse::desde).toList();
    }

    @Transactional
    public VeterinarioResponse crearVeterinario(VeterinarioRequest datos) {
        if (usuarioRepository.existsByUsername(datos.username())) {
            throw new ReglaNegocioException("El nombre de usuario ya existe");
        }
        Clinica clinica = clinicaRepository.findById(datos.clinicaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La clínica no existe"));
        return VeterinarioResponse.desde(registrarVeterinario(datos.username(), datos.password(), datos.nombreCompleto(),
                clinica, datos.numeroColegiado(), datos.telefono(), true));
    }

    /**
     * Veterinario de una solicitud aprobada: la cuenta existe pero no puede entrar (inactiva y con una
     * contraseña al azar que nadie conoce) hasta que use su enlace de activación.
     */
    @Transactional
    public Veterinario registrarVeterinarioSinAcceso(String nombre, Clinica clinica, String numeroColegiado,
                                                     String telefono, String email) {
        byte[] azar = new byte[32];
        new java.security.SecureRandom().nextBytes(azar);
        Veterinario veterinario = registrarVeterinario(usuarioDesdeEmail(email), java.util.Base64.getEncoder().encodeToString(azar),
                nombre, clinica, numeroColegiado, telefono, false);
        veterinario.setEmail(email);
        return veterinario;
    }

    private Veterinario registrarVeterinario(String username, String contrasena, String nombre, Clinica clinica,
                                             String numeroColegiado, String telefono, boolean activo) {
        if (veterinarioRepository.existsByNumeroColegiado(numeroColegiado)) {
            throw new ReglaNegocioException("Ya existe un veterinario con ese número de colegiado");
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(contrasena));
        usuario.setNombreCompleto(nombre);
        usuario.setRol(Rol.VETERINARIO);
        usuario.setActivo(activo);
        usuarioRepository.save(usuario);
        return veterinarioRepository.save(new Veterinario(usuario, clinica, numeroColegiado, telefono));
    }

    /** Nombre de usuario a partir del email; si ya existe, se le añade un número. */
    private String usuarioDesdeEmail(String email) {
        String base = email.substring(0, email.indexOf('@')).toLowerCase().replaceAll("[^a-z0-9._-]", "");
        base = base.isEmpty() ? "veterinario" : base.substring(0, Math.min(base.length(), 40));
        String candidato = base;
        for (int n = 2; usuarioRepository.existsByUsername(candidato); n++) {
            candidato = base + n;
        }
        return candidato;
    }

    // ----- Auxiliares -----

    @Transactional(readOnly = true)
    public List<AuxiliarResponse> listarAuxiliares() {
        return auxiliarRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(a -> a.getUsuario().getUsername()))
                .map(AuxiliarResponse::desde).toList();
    }

    @Transactional
    public AuxiliarResponse crearAuxiliar(AuxiliarRequest datos) {
        if (usuarioRepository.existsByUsername(datos.username())) {
            throw new ReglaNegocioException("El nombre de usuario ya existe");
        }
        Clinica clinica = clinicaRepository.findById(datos.clinicaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La clínica no existe"));

        Usuario usuario = new Usuario();
        usuario.setUsername(datos.username());
        usuario.setPassword(passwordEncoder.encode(datos.password()));
        usuario.setNombreCompleto(datos.nombreCompleto());
        usuario.setRol(Rol.AUXILIAR);
        usuarioRepository.save(usuario);

        Auxiliar auxiliar = new Auxiliar(usuario, clinica);
        return AuxiliarResponse.desde(auxiliarRepository.save(auxiliar));
    }
}
