package es.florida.vet.service;

import es.florida.vet.entity.ActivacionCuenta;
import es.florida.vet.entity.SolicitudAlta;
import es.florida.vet.entity.Usuario;
import es.florida.vet.entity.Veterinario;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.repository.ActivacionCuentaRepository;
import es.florida.vet.repository.SolicitudAltaRepository;
import es.florida.vet.repository.UsuarioRepository;
import es.florida.vet.repository.VeterinarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Activación del acceso: administración genera un enlace de un solo uso y lo entrega a mano (no hay
 * correo configurado); la persona elige su contraseña. Aprobar una clínica no activa ninguna cuenta.
 */
@Service
public class ActivacionCuentaService {

    private static final int DIAS_VALIDEZ = 7;
    private static final int LONGITUD_MINIMA = 8;
    private static final int LONGITUD_MAXIMA = 72; // BCrypt no usa más

    private final ActivacionCuentaRepository activacionRepository;
    private final SolicitudAltaRepository solicitudRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom azar = new SecureRandom();

    public ActivacionCuentaService(ActivacionCuentaRepository activacionRepository, SolicitudAltaRepository solicitudRepository,
                                   VeterinarioRepository veterinarioRepository, UsuarioRepository usuarioRepository,
                                   PasswordEncoder passwordEncoder) {
        this.activacionRepository = activacionRepository;
        this.solicitudRepository = solicitudRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record EnlaceActivacion(String token, String usuario, LocalDateTime caduca) {
    }

    /** El token solo se devuelve aquí, una vez: en la base de datos queda únicamente su hash. */
    @Transactional
    public EnlaceActivacion generarParaSolicitud(Long solicitudId) {
        SolicitudAlta s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no existe"));
        if (s.getVeterinarioId() == null) {
            throw new ReglaNegocioException("La solicitud todavía no tiene veterinario dado de alta");
        }
        Veterinario veterinario = veterinarioRepository.findById(s.getVeterinarioId()).orElseThrow();
        Usuario usuario = veterinario.getUsuario();
        if (usuario.isActivo()) {
            throw new ReglaNegocioException("Esta cuenta ya está activa");
        }
        activacionRepository.anularAnteriores(usuario.getId());
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime caduca = LocalDateTime.now().plusDays(DIAS_VALIDEZ);
        activacionRepository.save(new ActivacionCuenta(usuario, hash(token), caduca));
        return new EnlaceActivacion(token, usuario.getUsername(), caduca);
    }

    /** Devuelve el usuario con el que ya puede entrar. Enlace usado, caducado o inexistente dan el mismo error. */
    @Transactional
    public String activar(String token, String contrasena) {
        if (contrasena == null || contrasena.length() < LONGITUD_MINIMA || contrasena.length() > LONGITUD_MAXIMA) {
            throw new ReglaNegocioException("La contraseña debe tener entre " + LONGITUD_MINIMA + " y " + LONGITUD_MAXIMA + " caracteres");
        }
        ActivacionCuenta activacion = activacionRepository.findByTokenHash(hash(token == null ? "" : token))
                .filter(ActivacionCuenta::vigente)
                .orElseThrow(() -> new ReglaNegocioException("El enlace no es válido o ha caducado. Pide uno nuevo a la distribuidora."));
        Usuario usuario = activacion.getUsuario();
        usuario.setPassword(passwordEncoder.encode(contrasena));
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        activacion.marcarUsada();
        return usuario.getUsername();
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
