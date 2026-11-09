package es.florida.vet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.florida.vet.dto.SolicitudAltaRequest;
import es.florida.vet.dto.SolicitudAltaResponse;
import es.florida.vet.entity.Clinica;
import es.florida.vet.entity.OdooLog;
import es.florida.vet.entity.SolicitudAlta;
import es.florida.vet.entity.SolicitudDocumento;
import es.florida.vet.entity.SolicitudDocumento.ImagenValidada;
import es.florida.vet.enums.EstadoDocumentoSolicitud;
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
import es.florida.vet.repository.SolicitudDocumentoRepository;
import es.florida.vet.repository.VeterinarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final SolicitudDocumentoRepository documentoRepository;
    private final TransactionTemplate transaccion;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom azar = new SecureRandom();

    /** Lo comparten VetStock y Odoo por configuración del servidor. Sin él, se rechaza toda decisión. */
    @Value("${vetstock.odoo.secreto-solicitudes:}")
    private String secretoOdoo;

    public SolicitudAltaService(SolicitudAltaRepository solicitudRepository, AltaClinicaService altaClinicaService,
                                ClinicaRepository clinicaRepository, VeterinarioRepository veterinarioRepository,
                                OdooConnector odoo, OdooLogRepository odooLogRepository,
                                SolicitudDocumentoRepository documentoRepository, PlatformTransactionManager transacciones) {
        this.solicitudRepository = solicitudRepository;
        this.altaClinicaService = altaClinicaService;
        this.clinicaRepository = clinicaRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.odoo = odoo;
        this.odooLogRepository = odooLogRepository;
        this.documentoRepository = documentoRepository;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    // ----- Formulario público -----

    /**
     * Guarda la solicitud y devuelve su referencia. Solo guarda: Odoo se intenta aparte, para que el
     * formulario no dependa de él. Enviar lo mismo dentro de unos minutos (doble clic) devuelve la
     * misma referencia. La respuesta nunca dice si la clínica o el email ya existen.
     *
     * La solicitud y su imagen se guardan en UNA transacción: si algo falla no queda una solicitud
     * sin documento presentada como recibida, ni una imagen suelta.
     *
     * synchronized: la búsqueda y el guardado de dos envíos simultáneos no se pueden mezclar, y cada
     * transacción termina antes de que entre el siguiente. Hay una sola instancia del backend; con
     * varias haría falta un bloqueo en la base de datos.
     */
    public synchronized String registrar(SolicitudAltaRequest datos, ImagenValidada imagen) {
        // El campo trampa solo lo rellena un programa: se responde igual, sin guardar nada
        if (datos.sitioWeb() != null && !datos.sitioWeb().isBlank()) {
            return nuevaReferencia();
        }
        String huella = huella(datos, imagen);
        return transaccion.execute(estado -> solicitudRepository
                .findFirstByHuellaAndFechaCreacionAfterOrderByIdDesc(huella, LocalDateTime.now().minusMinutes(MINUTOS_REENVIO))
                .map(SolicitudAlta::getReferencia)
                .orElseGet(() -> {
                    SolicitudAlta nueva = new SolicitudAlta(nuevaReferencia(), huella, datos);
                    nueva.documentoRecibido();
                    solicitudRepository.save(nueva);
                    documentoRepository.save(new SolicitudDocumento(nueva.getId(), imagen, null));
                    return nueva.getReferencia();
                }));
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

    private String huella(SolicitudAltaRequest d, ImagenValidada imagen) {
        String texto = String.join("|", d.clinicaNombre().trim().toLowerCase(), d.email().trim().toLowerCase(),
                d.vetColegiado().trim().toLowerCase(), d.vetEmail().trim().toLowerCase(), imagen.sha256());
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

    /**
     * Primero el registro y después el documento. La solicitud solo queda al día cuando Odoo confirma
     * las dos cosas; si falla el adjunto, sigue pendiente y se reintenta sin duplicarlo.
     */
    private void enviarAOdoo(SolicitudAlta s) {
        OdooConnector.Resultado r = odoo.guardarSolicitudAlta(s.getReferencia(), s.getOdooId(), datosIniciales(s), datosDeIntegracion(s));
        if (r.exito()) {
            OdooConnector.Resultado adjunto = enviarDocumento(s.getId(), r.odooId());
            if (adjunto.exito()) {
                s.registrarEnvioOdoo(r.odooId());
            } else {
                s.registrarEnvioOdoo(r.odooId());
                s.registrarFalloOdoo("Documento sin enviar a Odoo: " + adjunto.detalle());
                r = adjunto;
            }
        } else {
            s.registrarFalloOdoo(r.detalle());
        }
        solicitudRepository.save(s);
        odooLogRepository.save(new OdooLog("SOLICITUD", s.getReferencia(), r.exito(), r.detalle(), r.odooId()));
    }

    private OdooConnector.Resultado enviarDocumento(Long solicitudId, Integer odooSolicitudId) {
        var resumen = documentoRepository.resumenVigente(solicitudId);
        if (resumen.isEmpty() || resumen.get().odooAdjuntoId() != null) {
            return OdooConnector.Resultado.ok(null, "Sin documento nuevo que enviar");
        }
        SolicitudDocumento documento = documentoRepository.findById(resumen.get().id()).orElseThrow();
        String nombre = "Acreditacion-" + documento.getId() + "." + documento.extension();
        OdooConnector.Resultado r = odoo.adjuntarDocumentoSolicitud(odooSolicitudId, documento.getId(), nombre,
                documento.getTipo(), documento.getContenido());
        if (r.exito()) {
            transaccion.executeWithoutResult(estado -> documentoRepository.registrarAdjuntoOdoo(documento.getId(), r.odooId()));
        }
        return r;
    }

    /** Solo se escriben al crear el registro en Odoo. */
    private Map<String, Object> datosIniciales(SolicitudAlta s) {
        Map<String, Object> d = new HashMap<>();
        d.put("fecha_solicitud", enUtc(s.getFechaCreacion()));
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
        d.put("documento_estado", s.getDocumentoEstado().name().toLowerCase());
        d.put("documento_revisado_por", vacio(s.getDocumentoRevisadoPor()));
        d.put("documento_fecha_revision", s.getDocumentoFechaRevision() == null ? false : enUtc(s.getDocumentoFechaRevision()));
        d.put("documento_observaciones", vacio(s.getDocumentoObservaciones()));
        if (s.getClinicaId() != null) {
            d.put("clinica_vetstock_id", s.getClinicaId());
            d.put("veterinario_vetstock_id", s.getVeterinarioId());
            clinicaRepository.findById(s.getClinicaId()).map(Clinica::getOdooId).ifPresent(id -> d.put("partner_id", id));
        }
        return d;
    }

    /** Odoo guarda las fechas en UTC. */
    private static String enUtc(LocalDateTime fecha) {
        return fecha.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).format(FECHA_ODOO);
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
                d.path("revisor").asText(), d.path("observaciones").asText(null),
                d.hasNonNull("documentoId") ? d.get("documentoId").asLong() : null);
    }

    SolicitudAltaResponse aplicarDecision(String referencia, String decision, String revisor, String nota, Long documentoId) {
        SolicitudAlta s = solicitudRepository.findByReferencia(referencia)
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no existe"));
        boolean conNota = nota != null && !nota.isBlank();
        switch (decision) {
            case "ACEPTAR_DOCUMENTO" -> {
                comprobarRevisable(s);
                var vigente = documentoRepository.resumenVigente(s.getId())
                        .orElseThrow(() -> new ReglaNegocioException("La solicitud no tiene documento que revisar"));
                // Se acepta exactamente el documento que se ha visto: si lo han sustituido, hay que volver a revisarlo
                if (!vigente.id().equals(documentoId)) {
                    throw new ReglaNegocioException("El documento ha cambiado. Revisa el nuevo antes de aceptarlo.");
                }
                if (s.getDocumentoEstado() != EstadoDocumentoSolicitud.ACEPTADO) {
                    s.revisarDocumento(EstadoDocumentoSolicitud.ACEPTADO, revisor, nota);
                    solicitudRepository.save(s);
                }
                return respuesta(s);
            }
            case "AUTORIZAR_EXCEPCION" -> {
                comprobarRevisable(s);
                if (s.getDocumentoEstado() != EstadoDocumentoSolicitud.SIN_DOCUMENTO) {
                    throw new ReglaNegocioException("La excepción solo es para solicitudes anteriores sin documento");
                }
                if (!conNota) {
                    throw new ReglaNegocioException("Escribe el motivo de la excepción en las observaciones internas");
                }
                s.revisarDocumento(EstadoDocumentoSolicitud.EXCEPCION, revisor, nota);
                return respuesta(solicitudRepository.save(s));
            }
            case "APROBAR" -> {
                if (s.getEstadoRevision() == EstadoRevisionSolicitud.RECHAZADA) {
                    throw new ReglaNegocioException("La solicitud está rechazada");
                }
                // Tener una imagen no basta: una persona tiene que haberla aceptado (o autorizado la excepción)
                if (s.getEstadoRevision() != EstadoRevisionSolicitud.APROBADA && !s.getDocumentoEstado().permiteAprobar()) {
                    throw new ReglaNegocioException("Antes de aprobar hay que revisar y aceptar el documento acreditativo");
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
                if ("RECHAZAR".equals(decision) && !conNota) {
                    throw new ReglaNegocioException("Escribe el motivo del rechazo en las observaciones internas");
                }
                s.registrarRevision("RECHAZAR".equals(decision) ? EstadoRevisionSolicitud.RECHAZADA
                        : EstadoRevisionSolicitud.REQUIERE_INFORMACION, revisor, nota);
                s.marcarAlta(EstadoAltaSolicitud.NO_INICIADA, null);
                return respuesta(solicitudRepository.save(s));
            }
            default -> throw new ReglaNegocioException("Decisión desconocida");
        }
    }

    private void comprobarRevisable(SolicitudAlta s) {
        if (s.getEstadoRevision() == EstadoRevisionSolicitud.RECHAZADA || s.getEstadoRevision() == EstadoRevisionSolicitud.APROBADA) {
            throw new ReglaNegocioException("La solicitud ya está decidida");
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
        Map<Long, SolicitudDocumentoRepository.Resumen> documentos = new HashMap<>();
        documentoRepository.resumenesVigentes().forEach(d -> documentos.put(d.solicitudId(), d));
        return solicitudRepository.findAllByOrderByIdDesc().stream()
                .map(s -> SolicitudAltaResponse.desde(s, acceso(s), documentos.get(s.getId()))).toList();
    }

    /** Solo administración (ver SecurityConfig). La imagen nunca se sirve en una ruta pública. */
    @Transactional(readOnly = true)
    public SolicitudDocumento documentoVigente(Long solicitudId) {
        return documentoRepository.findFirstBySolicitudIdAndVigenteTrue(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no tiene documento"));
    }

    /**
     * Administración sustituye el documento cuando la clínica envía otro por el canal habitual de la
     * distribuidora. El anterior se conserva y la revisión vuelve a empezar. Una solicitud decidida
     * no se modifica.
     */
    public SolicitudAltaResponse sustituirDocumento(Long solicitudId, ImagenValidada imagen, String administrador) {
        transaccion.executeWithoutResult(estado -> {
            SolicitudAlta s = solicitudRepository.bloquear(solicitudId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no existe"));
            comprobarRevisable(s);
            documentoRepository.retirarVigente(solicitudId);
            documentoRepository.save(new SolicitudDocumento(solicitudId, imagen, administrador));
            s.documentoRecibido();
            solicitudRepository.save(s);
        });
        return respuesta(solicitudRepository.findById(solicitudId).orElseThrow());
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
        return SolicitudAltaResponse.desde(s, acceso(s), documentoRepository.resumenVigente(s.getId()).orElse(null));
    }

    private Boolean acceso(SolicitudAlta s) {
        return s.getVeterinarioId() == null ? null
                : veterinarioRepository.findById(s.getVeterinarioId()).map(v -> v.getUsuario().isActivo()).orElse(null);
    }
}
