package es.florida.vet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.florida.vet.dto.SolicitudAltaRequest;
import es.florida.vet.dto.SolicitudAltaResponse;
import es.florida.vet.entity.Clinica;
import es.florida.vet.entity.OdooLog;
import es.florida.vet.entity.SolicitudAlta;
import es.florida.vet.entity.Veterinario;
import es.florida.vet.enums.EstadoAltaSolicitud;
import es.florida.vet.enums.EstadoRevisionSolicitud;
import es.florida.vet.exception.AccesoDenegadoException;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.odoo.OdooConnector;
import es.florida.vet.repository.ClinicaRepository;
import es.florida.vet.repository.OdooLogRepository;
import es.florida.vet.repository.SolicitudAltaRepository;
import es.florida.vet.repository.VeterinarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Solicitudes de alta: formulario público → guardar → enviar a Odoo → revisión de una persona →
 * alta. VetStock es responsable de los datos y del alta; Odoo, de la decisión de revisión.
 */
@Service
public class SolicitudAltaService {

    public static final String CABECERA_FIRMA = "X-Firma-Odoo";
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MINUTOS_REENVIO = 10;
    private static final long MARGEN_FIRMA_SEGUNDOS = 300;
    private static final DateTimeFormatter FECHA_ODOO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SolicitudAltaRepository solicitudRepository;
    private final AltaClinicaService altaClinicaService;
    private final ClinicaRepository clinicaRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final OdooConnector odoo;
    private final OdooLogRepository odooLogRepository;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom azar = new SecureRandom();

    /** Lo comparten VetStock y Odoo por configuración del servidor. Sin él, se rechaza toda decisión. */
    @Value("${vetstock.odoo.secreto-solicitudes:}")
    private String secretoOdoo;

    public SolicitudAltaService(SolicitudAltaRepository solicitudRepository, AltaClinicaService altaClinicaService,
                                ClinicaRepository clinicaRepository, VeterinarioRepository veterinarioRepository,
                                OdooConnector odoo, OdooLogRepository odooLogRepository) {
        this.solicitudRepository = solicitudRepository;
        this.altaClinicaService = altaClinicaService;
        this.clinicaRepository = clinicaRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.odoo = odoo;
        this.odooLogRepository = odooLogRepository;
    }

    // ----- Formulario público -----

    /**
     * Guarda la solicitud y devuelve su referencia. Solo guarda: Odoo se intenta aparte, para que el
     * formulario no dependa de él. Enviar lo mismo dentro de unos minutos (doble clic) devuelve la
     * misma referencia. La respuesta nunca dice si la clínica o el email ya existen.
     *
     * synchronized y sin @Transactional a propósito: la búsqueda y el guardado de dos envíos simultáneos
     * no se pueden mezclar, y cada guardado queda confirmado antes de que entre el siguiente. Hay una
     * sola instancia del backend; con varias haría falta un bloqueo en la base de datos.
     */
    public synchronized String registrar(SolicitudAltaRequest datos) {
        // El campo trampa solo lo rellena un programa: se responde igual, sin guardar nada
        if (datos.sitioWeb() != null && !datos.sitioWeb().isBlank()) {
            return nuevaReferencia();
        }
        String huella = huella(datos);
        return solicitudRepository
                .findFirstByHuellaAndFechaCreacionAfterOrderByIdDesc(huella, LocalDateTime.now().minusMinutes(MINUTOS_REENVIO))
                .map(SolicitudAlta::getReferencia)
                .orElseGet(() -> solicitudRepository.save(new SolicitudAlta(nuevaReferencia(), huella, datos)).getReferencia());
    }

    private String nuevaReferencia() {
        String referencia;
        do {
            StringBuilder texto = new StringBuilder("SOL-");
            for (int i = 0; i < 8; i++) {
                texto.append(LETRAS.charAt(azar.nextInt(LETRAS.length())));
            }
            referencia = texto.toString();
        } while (solicitudRepository.existsByReferencia(referencia));
        return referencia;
    }

    private String huella(SolicitudAltaRequest d) {
        String texto = String.join("|", d.clinicaNombre().trim().toLowerCase(), d.email().trim().toLowerCase(),
                d.vetColegiado().trim().toLowerCase(), d.vetEmail().trim().toLowerCase());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ----- Envío a Odoo con reintentos -----

    /** Envía lo pendiente. Si Odoo no responde, la solicitud sigue guardada y se reintenta en la próxima pasada. */
    @Scheduled(fixedDelayString = "${vetstock.solicitudes.revision-ms:60000}", initialDelayString = "${vetstock.solicitudes.revision-ms:60000}")
    public void enviarPendientesAOdoo() {
        solicitudRepository.findByOdooPendienteTrue().forEach(this::enviarAOdoo);
    }

    private void enviarAOdoo(SolicitudAlta s) {
        OdooConnector.Resultado r = odoo.guardarSolicitudAlta(s.getReferencia(), s.getOdooId(), datosIniciales(s), datosDeIntegracion(s));
        if (r.exito()) {
            s.registrarEnvioOdoo(r.odooId());
        } else {
            s.registrarFalloOdoo(r.detalle());
        }
        solicitudRepository.save(s);
        odooLogRepository.save(new OdooLog("SOLICITUD", s.getReferencia(), r.exito(), r.detalle(), r.odooId()));
    }

    /** Solo se escriben al crear el registro en Odoo. */
    private Map<String, Object> datosIniciales(SolicitudAlta s) {
        Map<String, Object> d = new HashMap<>();
        d.put("fecha_solicitud", s.getFechaCreacion().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).format(FECHA_ODOO));
        d.put("clinica_nombre", s.getClinicaNombre());
        d.put("razon_social", vacio(s.getRazonSocial()));
        d.put("identificador_fiscal", vacio(s.getIdentificadorFiscal()));
        d.put("email", s.getEmail());
        d.put("telefono", s.getTelefono());
        d.put("direccion", s.getDireccion());
        d.put("codigo_postal", s.getCodigoPostal());
        d.put("localidad", s.getLocalidad());
        d.put("provincia", s.getProvincia());
        d.put("pais", s.getPais());
        d.put("vet_nombre", s.getVetNombre());
        d.put("vet_colegiado", s.getVetColegiado());
        d.put("vet_colegio", s.getVetColegio());
        d.put("vet_email", s.getVetEmail());
        d.put("vet_telefono", vacio(s.getVetTelefono()));
        d.put("observaciones", vacio(s.getObservaciones()));
        return d;
    }

    /** El estado del alta lo decide VetStock, así que se actualiza siempre. La revisión no se toca: es de Odoo. */
    private Map<String, Object> datosDeIntegracion(SolicitudAlta s) {
        Map<String, Object> d = new HashMap<>();
        d.put("estado_alta", s.getEstadoAlta().name().toLowerCase());
        d.put("mensaje_alta", vacio(s.getMensajeAlta()));
        if (s.getClinicaId() != null) {
            d.put("clinica_vetstock_id", s.getClinicaId());
            d.put("veterinario_vetstock_id", s.getVeterinarioId());
            clinicaRepository.findById(s.getClinicaId()).map(Clinica::getOdooId).ifPresent(id -> d.put("partner_id", id));
        }
        return d;
    }

    private static String vacio(String texto) {
        return texto == null ? "" : texto;
    }

    // ----- Decisión tomada en Odoo -----

    /** Punto de entrada desde Odoo: comprueba la firma del cuerpo exacto y que la llamada sea reciente. */
    public SolicitudAltaResponse decisionFirmada(String cuerpo, String firma) {
        if (secretoOdoo == null || secretoOdoo.isBlank() || firma == null
                || !MessageDigest.isEqual(FirmaHmac.calcular(secretoOdoo, cuerpo).getBytes(StandardCharsets.UTF_8), firma.getBytes(StandardCharsets.UTF_8))) {
            throw new AccesoDenegadoException("Firma no válida");
        }
        JsonNode d;
        try {
            d = json.readTree(cuerpo);
        } catch (Exception e) {
            throw new ReglaNegocioException("El cuerpo no es válido");
        }
        if (Math.abs(Instant.now().getEpochSecond() - d.path("marcaTiempo").asLong()) > MARGEN_FIRMA_SEGUNDOS) {
            throw new AccesoDenegadoException("La llamada ha caducado");
        }
        return aplicarDecision(d.path("referencia").asText(), d.path("decision").asText(),
                d.path("revisor").asText(), d.path("observaciones").asText(null));
    }

    SolicitudAltaResponse aplicarDecision(String referencia, String decision, String revisor, String nota) {
        SolicitudAlta s = solicitudRepository.findByReferencia(referencia)
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no existe"));
        switch (decision) {
            case "APROBAR" -> {
                if (s.getEstadoRevision() == EstadoRevisionSolicitud.RECHAZADA) {
                    throw new ReglaNegocioException("La solicitud está rechazada");
                }
                // Repetir la aprobación conserva quién y cuándo aprobó, y continúa el alta si quedó a medias
                if (s.getEstadoRevision() != EstadoRevisionSolicitud.APROBADA) {
                    s.registrarRevision(EstadoRevisionSolicitud.APROBADA, revisor, nota);
                    solicitudRepository.save(s);
                }
                return continuarAlta(s.getId());
            }
            case "RECHAZAR", "REQUIERE_INFORMACION" -> {
                if (s.getClinicaId() != null) {
                    throw new ReglaNegocioException("La clínica ya está dada de alta");
                }
                s.registrarRevision("RECHAZAR".equals(decision) ? EstadoRevisionSolicitud.RECHAZADA
                        : EstadoRevisionSolicitud.REQUIERE_INFORMACION, revisor, nota);
                s.marcarAlta(EstadoAltaSolicitud.NO_INICIADA, null);
                return respuesta(solicitudRepository.save(s));
            }
            default -> throw new ReglaNegocioException("Decisión desconocida");
        }
    }

    /** Sin @Transactional a propósito: cada paso queda guardado aunque falle el siguiente. */
    private SolicitudAltaResponse continuarAlta(Long id) {
        altaClinicaService.crearRegistrosLocales(id);
        SolicitudAlta s = solicitudRepository.findById(id).orElseThrow();
        if (s.getEstadoAlta() == EstadoAltaSolicitud.REQUIERE_REVISION) {
            return respuesta(s);
        }

        Clinica clinica = clinicaRepository.findById(s.getClinicaId()).orElseThrow();
        Veterinario veterinario = veterinarioRepository.findById(s.getVeterinarioId()).orElseThrow();

        OdooConnector.Resultado empresa = odoo.crearCliente(new OdooConnector.FichaCliente(clinica.getNombre(),
                clinica.getDireccion(), clinica.getTelefono(), veterinario.getUsuario().getNombreCompleto(), clinica.getOdooId()));
        if (!empresa.exito()) {
            return fallo(s, "No se pudo crear la clínica en Odoo", empresa.detalle());
        }
        clinica.setOdooId(empresa.odooId());
        clinica.setOdooSincronizado(LocalDateTime.now());
        clinicaRepository.save(clinica);

        OdooConnector.Resultado persona = odoo.guardarContactoVeterinario(new OdooConnector.FichaContactoVeterinario(
                veterinario.getUsuario().getNombreCompleto(), veterinario.getEmail(), veterinario.getTelefono(),
                veterinario.getNumeroColegiado(), clinica.getOdooId(), veterinario.getOdooContactoId()));
        if (!persona.exito()) {
            return fallo(s, "No se pudo crear el contacto del veterinario en Odoo", persona.detalle());
        }
        veterinario.setOdooContactoId(persona.odooId());
        veterinarioRepository.save(veterinario);

        s.marcarAlta(EstadoAltaSolicitud.COMPLETADA, "Alta completada. El acceso está pendiente de activar.");
        odooLogRepository.save(new OdooLog("SOLICITUD", s.getReferencia(), true, "Alta completada", empresa.odooId()));
        return respuesta(solicitudRepository.save(s));
    }

    private SolicitudAltaResponse fallo(SolicitudAlta s, String mensaje, String detalle) {
        s.marcarAlta(EstadoAltaSolicitud.FALLIDA, mensaje);
        odooLogRepository.save(new OdooLog("SOLICITUD", s.getReferencia(), false, detalle, null));
        return respuesta(solicitudRepository.save(s));
    }

    // ----- Administración -----

    @Transactional(readOnly = true)
    public List<SolicitudAltaResponse> listar() {
        return solicitudRepository.findAllByOrderByIdDesc().stream().map(this::respuesta).toList();
    }

    /** Reintenta el envío a Odoo y, si está aprobada y sin terminar, continúa el alta donde se quedó. */
    public SolicitudAltaResponse reintentar(Long id) {
        SolicitudAlta s = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no existe"));
        if (s.getEstadoRevision() == EstadoRevisionSolicitud.APROBADA && s.getEstadoAlta() != EstadoAltaSolicitud.COMPLETADA) {
            continuarAlta(id);
            s = solicitudRepository.findById(id).orElseThrow();
        }
        enviarAOdoo(s);
        return respuesta(solicitudRepository.findById(id).orElseThrow());
    }

    private SolicitudAltaResponse respuesta(SolicitudAlta s) {
        Boolean acceso = s.getVeterinarioId() == null ? null
                : veterinarioRepository.findById(s.getVeterinarioId()).map(v -> v.getUsuario().isActivo()).orElse(null);
        return SolicitudAltaResponse.desde(s, acceso);
    }
}
