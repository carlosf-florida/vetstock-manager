package es.florida.vet.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.florida.vet.odoo.OdooConnector;
import es.florida.vet.repository.ActivacionCuentaRepository;
import es.florida.vet.repository.ClinicaRepository;
import es.florida.vet.repository.SolicitudAltaRepository;
import es.florida.vet.repository.SolicitudDocumentoRepository;
import es.florida.vet.service.ValidadorImagen;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import es.florida.vet.repository.VeterinarioRepository;
import es.florida.vet.service.FirmaHmac;
import es.florida.vet.service.SolicitudAltaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recorrido de una solicitud de alta: formulario, decisión firmada, alta, reintentos y activación. */
class SolicitudesAltaIntegracionTest extends IntegracionBase {

    private static final String SECRETO = "secreto-odoo-solo-para-pruebas";
    private static final String URL_FORMULARIO = "/api/publico/solicitudes-alta";

    @Autowired private SolicitudAltaRepository solicitudes;
    @Autowired private ClinicaRepository clinicas;
    @Autowired private VeterinarioRepository veterinarios;
    @Autowired private ActivacionCuentaRepository activaciones;
    @Autowired private SolicitudAltaService servicio;
    @Autowired private SolicitudDocumentoRepository documentos;
    @Autowired private ValidadorImagen validador;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private OdooConnector odoo;

    @DynamicPropertySource
    static void secreto(DynamicPropertyRegistry registro) {
        registro.add("vetstock.odoo.secreto-solicitudes", () -> SECRETO);
        registro.add("vetstock.solicitudes.revision-ms", () -> "86400000");
        registro.add("vetstock.solicitudes.max-por-hora", () -> "1000");
    }

    // ----- Datos -----

    private static String unico() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private ObjectNode formulario(String marca) {
        ObjectNode d = mapper.createObjectNode();
        d.put("clinicaNombre", "Clínica Prueba " + marca);
        d.put("identificadorFiscal", "F" + marca);
        d.put("email", "clinica" + marca.toLowerCase() + "@ejemplo.test");
        d.put("telefono", "600000000");
        d.put("direccion", "Calle Prueba 1");
        d.put("codigoPostal", "46000");
        d.put("localidad", "Valencia");
        d.put("provincia", "Valencia");
        d.put("pais", "España");
        d.put("vetNombre", "Vet Prueba " + marca);
        d.put("vetColegiado", "C" + marca);
        d.put("vetColegio", "Valencia");
        d.put("vetEmail", "vet" + marca.toLowerCase() + "@ejemplo.test");
        d.put("privacidadLeida", true);
        return d;
    }

    /** Imagen de prueba generada en memoria (nunca un documento real). */
    private static byte[] imagen(String formato, int ancho, int alto) throws Exception {
        BufferedImage dibujo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        dibujo.getGraphics().fillRect(10, 10, ancho / 2, alto / 2);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(dibujo, formato, salida);
        return salida.toByteArray();
    }

    private static byte[] PNG_PRUEBA;

    private static MockMultipartFile documentoPrueba() throws Exception {
        if (PNG_PRUEBA == null) {
            PNG_PRUEBA = imagen("png", 400, 300);
        }
        return new MockMultipartFile("documento", "acreditacion.png", "image/png", PNG_PRUEBA);
    }

    private MockMultipartFile parteDatos(ObjectNode datos) {
        return new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE, datos.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private ResultActions enviar(ObjectNode datos) throws Exception {
        return enviarCon(datos, documentoPrueba());
    }

    private ResultActions enviarCon(ObjectNode datos, MockMultipartFile documento) throws Exception {
        var peticion = multipart(URL_FORMULARIO).file(parteDatos(datos));
        if (documento != null) {
            peticion.file(documento);
        }
        return mvc.perform(peticion);
    }

    private long idDe(String referencia) {
        return solicitudes.findByReferencia(referencia).orElseThrow().getId();
    }

    private String crearSolicitud(ObjectNode datos) throws Exception {
        String cuerpo = enviar(datos).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(cuerpo).get("referencia").asText();
    }

    private ResultActions decision(String cuerpo, String firma) throws Exception {
        var peticion = post("/api/integracion/odoo/solicitudes/decision").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (firma != null) {
            peticion.header(SolicitudAltaService.CABECERA_FIRMA, firma);
        }
        return mvc.perform(peticion);
    }

    private String cuerpoDecision(String referencia, String tipo, long marcaTiempo) {
        return cuerpoDecision(referencia, tipo, marcaTiempo, "ok", documentoVigente(referencia));
    }

    private String cuerpoDecision(String referencia, String tipo, long marcaTiempo, String nota, Long documentoId) {
        ObjectNode cuerpo = mapper.createObjectNode().put("referencia", referencia).put("decision", tipo)
                .put("revisor", "Revisora Odoo").put("observaciones", nota).put("marcaTiempo", marcaTiempo);
        if (documentoId != null) {
            cuerpo.put("documentoId", documentoId);
        }
        return cuerpo.toString();
    }

    private Long documentoVigente(String referencia) {
        return solicitudes.findByReferencia(referencia)
                .flatMap(s -> documentos.resumenVigente(s.getId())).map(SolicitudDocumentoRepository.Resumen::id).orElse(null);
    }

    private ResultActions firmada(String cuerpo) throws Exception {
        return decision(cuerpo, FirmaHmac.calcular(SECRETO, cuerpo));
    }

    private JsonNode decidir(String referencia, String tipo) throws Exception {
        String cuerpo = cuerpoDecision(referencia, tipo, Instant.now().getEpochSecond());
        String texto = firmada(cuerpo).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(texto);
    }

    /** Como en Odoo: primero se acepta el documento y después se aprueba. */
    private JsonNode aprobar(String referencia) throws Exception {
        if ("PENDIENTE".equals(solicitudes.findByReferencia(referencia).orElseThrow().getDocumentoEstado().name())) {
            decidir(referencia, "ACEPTAR_DOCUMENTO");
        }
        return decidir(referencia, "APROBAR");
    }

    // ----- Formulario -----

    @Test
    void solicitudValidaQuedaPendienteSinCrearClinicaNiCuenta() throws Exception {
        String marca = unico();
        long clinicasAntes = clinicas.count();
        String referencia = crearSolicitud(formulario(marca));

        assertTrue(referencia.startsWith("SOL-"));
        var guardada = solicitudes.findByReferencia(referencia).orElseThrow();
        assertEquals("PENDIENTE", guardada.getEstadoRevision().name());
        assertEquals("NO_INICIADA", guardada.getEstadoAlta().name());
        assertTrue(guardada.isOdooPendiente());
        assertEquals(clinicasAntes, clinicas.count());
        assertFalse(veterinarios.existsByNumeroColegiado("C" + marca));
    }

    @Test
    void camposObligatoriosYFormatoSeValidanEnServidor() throws Exception {
        ObjectNode sinNombre = formulario(unico());
        sinNombre.remove("clinicaNombre");
        enviar(sinNombre).andExpect(status().isBadRequest());

        ObjectNode emailMalo = formulario(unico());
        emailMalo.put("email", "no-es-un-email");
        enviar(emailMalo).andExpect(status().isBadRequest());

        ObjectNode largo = formulario(unico());
        largo.put("clinicaNombre", "x".repeat(500));
        enviar(largo).andExpect(status().isBadRequest());

        ObjectNode sinPrivacidad = formulario(unico());
        sinPrivacidad.put("privacidadLeida", false);
        enviar(sinPrivacidad).andExpect(status().isBadRequest());
    }

    @Test
    void dobleEnvioDevuelveLaMismaReferencia() throws Exception {
        ObjectNode datos = formulario(unico());
        assertEquals(crearSolicitud(datos), crearSolicitud(datos));
    }

    @Test
    void enviosSimultaneosCreanUnaSolaSolicitud() throws Exception {
        String marca = unico();
        var datos = mapper.treeToValue(formulario(marca), es.florida.vet.dto.SolicitudAltaRequest.class);
        var hilos = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var imagen = validador.validar(documentoPrueba());
            var tareas = java.util.Collections.nCopies(4, (java.util.concurrent.Callable<String>) () -> servicio.registrar(datos, imagen));
            var referencias = new java.util.HashSet<String>();
            for (var resultado : hilos.invokeAll(tareas)) {
                referencias.add(resultado.get());
            }
            assertEquals(1, referencias.size());
        } finally {
            hilos.shutdown();
        }
    }

    @Test
    void campoTrampaRespondeIgualPeroNoGuardaNada() throws Exception {
        ObjectNode datos = formulario(unico());
        datos.put("sitioWeb", "http://spam.test");
        String referencia = crearSolicitud(datos);
        assertTrue(referencia.startsWith("SOL-"));
        assertTrue(solicitudes.findByReferencia(referencia).isEmpty());
    }

    @Test
    void laRespuestaPublicaNoRevelaDatosNiSiYaExisten() throws Exception {
        String marca = unico();
        crearSolicitud(formulario(marca));
        ObjectNode repetida = formulario(marca);
        repetida.put("clinicaNombre", "Otra " + marca);
        String texto = enviar(repetida).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(1, mapper.readTree(texto).size());
    }

    // ----- Permisos -----

    @Test
    void elListadoYLasAccionesSonSoloDeAdministracion() throws Exception {
        String url = "/api/solicitudes-alta";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)).andExpect(status().isUnauthorized());
        getJson(drGarcia(), url).andExpect(status().isForbidden());
        getJson(auxSanRoque(), url).andExpect(status().isForbidden());
        getJson(admin(), url).andExpect(status().isOk());
        mvc.perform(post(url + "/1/reintentar").session(drGarcia())).andExpect(status().isForbidden());
        mvc.perform(post(url + "/1/activacion").session(auxSanRoque())).andExpect(status().isForbidden());
    }

    @Test
    void decisionSinFirmaConFirmaMalaOAntiguaSeRechazaYNoAprueba() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        long ahora = Instant.now().getEpochSecond();
        String cuerpo = cuerpoDecision(referencia, "APROBAR", ahora);

        decision(cuerpo, null).andExpect(status().isForbidden());
        decision(cuerpo, FirmaHmac.calcular("otro-secreto", cuerpo)).andExpect(status().isForbidden());
        // Firma válida de otro cuerpo: el contenido ha cambiado
        decision(cuerpoDecision(referencia, "RECHAZAR", ahora), FirmaHmac.calcular(SECRETO, cuerpo)).andExpect(status().isForbidden());
        String antiguo = cuerpoDecision(referencia, "APROBAR", ahora - 3600);
        decision(antiguo, FirmaHmac.calcular(SECRETO, antiguo)).andExpect(status().isForbidden());

        assertEquals("PENDIENTE", solicitudes.findByReferencia(referencia).orElseThrow().getEstadoRevision().name());
    }

    // ----- Revisión y alta -----

    @Test
    void rechazarNoCreaNadaYNoSePuedeAprobarDespues() throws Exception {
        String marca = unico();
        String referencia = crearSolicitud(formulario(marca));
        long antes = clinicas.count();

        JsonNode r = decidir(referencia, "RECHAZAR");
        assertEquals("RECHAZADA", r.get("estadoRevision").asText());
        assertEquals(antes, clinicas.count());

        String cuerpo = cuerpoDecision(referencia, "APROBAR", Instant.now().getEpochSecond());
        decision(cuerpo, FirmaHmac.calcular(SECRETO, cuerpo)).andExpect(status().isBadRequest());
        assertEquals(antes, clinicas.count());
    }

    @Test
    void requiereInformacionNoCreaNadaYLuegoPuedeAprobarse() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        long antes = clinicas.count();
        assertEquals("REQUIERE_INFORMACION", decidir(referencia, "REQUIERE_INFORMACION").get("estadoRevision").asText());
        assertEquals(antes, clinicas.count());
        assertEquals("COMPLETADA", aprobar(referencia).get("estadoAlta").asText());
    }

    @Test
    void aprobarCreaClinicaYVeterinarioSinAccesoYRegistraIds() throws Exception {
        String marca = unico();
        String referencia = crearSolicitud(formulario(marca));
        JsonNode r = aprobar(referencia);

        assertEquals("APROBADA", r.get("estadoRevision").asText());
        assertEquals("COMPLETADA", r.get("estadoAlta").asText());
        assertEquals("Revisora Odoo", r.get("revisadoPor").asText());
        assertFalse(r.get("accesoActivo").asBoolean());

        var veterinario = veterinarios.findByNumeroColegiado("C" + marca).orElseThrow();
        assertFalse(veterinario.getUsuario().isActivo());
        assertNotNull(veterinario.getOdooContactoId());
        var clinica = clinicas.findById(r.get("clinicaId").asLong()).orElseThrow();
        assertEquals(referencia, clinica.getSolicitudReferencia());
        assertEquals(veterinario.getId(), clinica.getVeterinarioResponsable().getId());
        assertNotNull(clinica.getOdooId());

        // La cuenta sin activar no puede iniciar sesión
        mvc.perform(post("/api/auth/login").param("username", veterinario.getUsuario().getUsername()).param("password", "cualquiera"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aprobarDosVecesNoDuplicaYConservaLaPrimeraRevision() throws Exception {
        String marca = unico();
        String referencia = crearSolicitud(formulario(marca));
        JsonNode primera = aprobar(referencia);
        long clinicasTrasPrimera = clinicas.count();
        long veterinariosTrasPrimera = veterinarios.count();

        JsonNode segunda = aprobar(referencia);

        assertEquals(primera.get("clinicaId").asLong(), segunda.get("clinicaId").asLong());
        assertEquals(primera.get("veterinarioId").asLong(), segunda.get("veterinarioId").asLong());
        assertEquals(primera.get("fechaRevision").asText(), segunda.get("fechaRevision").asText());
        assertEquals(clinicasTrasPrimera, clinicas.count());
        assertEquals(veterinariosTrasPrimera, veterinarios.count());
    }

    @Test
    void laClinicaDadaDeAltaNoSePuedeRechazar() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        aprobar(referencia);
        String cuerpo = cuerpoDecision(referencia, "RECHAZAR", Instant.now().getEpochSecond());
        decision(cuerpo, FirmaHmac.calcular(SECRETO, cuerpo)).andExpect(status().isBadRequest());
        assertEquals("APROBADA", solicitudes.findByReferencia(referencia).orElseThrow().getEstadoRevision().name());
    }

    // ----- Duplicados -----

    @Test
    void mismoFiscalConOtroVeterinarioPasaARevisionSinTocarLaClinica() throws Exception {
        String marca = unico();
        JsonNode original = aprobar(crearSolicitud(formulario(marca)));
        Long responsable = clinicas.findById(original.get("clinicaId").asLong()).orElseThrow().getVeterinarioResponsable().getId();

        ObjectNode otra = formulario(unico());
        otra.put("identificadorFiscal", "F" + marca);
        long antes = clinicas.count();
        JsonNode r = aprobar(crearSolicitud(otra));

        assertEquals("REQUIERE_REVISION", r.get("estadoAlta").asText());
        assertTrue(r.get("clinicaId").isNull());
        assertEquals(antes, clinicas.count());
        assertEquals(responsable, clinicas.findById(original.get("clinicaId").asLong()).orElseThrow().getVeterinarioResponsable().getId());
    }

    @Test
    void mismoFiscalYMismoColegiadoSeVinculaSinDuplicar() throws Exception {
        String marca = unico();
        JsonNode original = aprobar(crearSolicitud(formulario(marca)));

        ObjectNode otra = formulario(marca);
        otra.put("email", "otro" + marca.toLowerCase() + "@ejemplo.test"); // otra huella: no es un doble clic
        otra.put("telefono", "611111111");
        long antes = clinicas.count();
        JsonNode r = aprobar(crearSolicitud(otra));

        assertEquals("COMPLETADA", r.get("estadoAlta").asText());
        assertEquals(original.get("clinicaId").asLong(), r.get("clinicaId").asLong());
        assertEquals(antes, clinicas.count());
        assertEquals("600000000", clinicas.findById(r.get("clinicaId").asLong()).orElseThrow().getTelefono());
    }

    @Test
    void colegiadoEnOtraClinicaONombreIgualPasanARevision() throws Exception {
        String marca = unico();
        aprobar(crearSolicitud(formulario(marca)));

        ObjectNode mismoColegiado = formulario(unico());
        mismoColegiado.put("vetColegiado", "C" + marca);
        assertEquals("REQUIERE_REVISION", aprobar(crearSolicitud(mismoColegiado)).get("estadoAlta").asText());

        ObjectNode mismoNombre = formulario(unico());
        mismoNombre.put("clinicaNombre", ("Clínica Prueba " + marca).toUpperCase());
        assertEquals("REQUIERE_REVISION", aprobar(crearSolicitud(mismoNombre)).get("estadoAlta").asText());
    }

    // ----- Fallos de Odoo -----

    @Test
    void siOdooFallaLaSolicitudSeConservaYElReintentoNoDuplica() throws Exception {
        String marca = unico();
        String referencia = crearSolicitud(formulario(marca));
        doReturn(OdooConnector.Resultado.fallo("Odoo no responde")).when(odoo).guardarContactoVeterinario(any());
        JsonNode fallida;
        try {
            fallida = aprobar(referencia);
        } finally {
            doCallRealMethod().when(odoo).guardarContactoVeterinario(any());
        }

        assertEquals("FALLIDA", fallida.get("estadoAlta").asText());
        Long clinicaId = fallida.get("clinicaId").asLong();
        var clinica = clinicas.findById(clinicaId).orElseThrow();
        assertNotNull(clinica.getOdooId()); // el paso que sí funcionó queda guardado
        assertNull(veterinarios.findByNumeroColegiado("C" + marca).orElseThrow().getOdooContactoId());
        long antes = clinicas.count();

        String texto = mvc.perform(post("/api/solicitudes-alta/" + fallida.get("id").asLong() + "/reintentar").session(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode reintento = mapper.readTree(texto);

        assertEquals("COMPLETADA", reintento.get("estadoAlta").asText());
        assertEquals(clinicaId, reintento.get("clinicaId").asLong());
        assertEquals(antes, clinicas.count());
        assertEquals(clinica.getOdooId(), clinicas.findById(clinicaId).orElseThrow().getOdooId());
    }

    @Test
    void siOdooNoRecibeLaSolicitudQuedaPendienteConElError() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        doReturn(OdooConnector.Resultado.fallo("Odoo no responde")).when(odoo).guardarSolicitudAlta(any(), any(), any(), any());
        try {
            servicio.enviarPendientesAOdoo();
        } finally {
            doCallRealMethod().when(odoo).guardarSolicitudAlta(any(), any(), any(), any());
        }
        var guardada = solicitudes.findByReferencia(referencia).orElseThrow();
        assertTrue(guardada.isOdooPendiente());
        assertNotNull(guardada.getOdooUltimoError());

        servicio.enviarPendientesAOdoo();
        var enviada = solicitudes.findByReferencia(referencia).orElseThrow();
        assertFalse(enviada.isOdooPendiente());
        assertNotNull(enviada.getOdooId());
        assertNull(enviada.getOdooUltimoError());
    }

    // ----- Activación -----

    private JsonNode generarEnlace(long solicitudId) throws Exception {
        String texto = mvc.perform(post("/api/solicitudes-alta/" + solicitudId + "/activacion").session(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(texto);
    }

    private ResultActions activar(String token, String contrasena) throws Exception {
        String cuerpo = mapper.createObjectNode().put("token", token).put("contrasena", contrasena).toString();
        return mvc.perform(post("/api/publico/activacion").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    void elEnlaceActivaLaCuentaUnaSolaVezYNoGuardaElToken() throws Exception {
        String marca = unico();
        JsonNode alta = aprobar(crearSolicitud(formulario(marca)));
        JsonNode enlace = generarEnlace(alta.get("id").asLong());
        String token = enlace.get("token").asText();
        String usuario = enlace.get("usuario").asText();

        assertTrue(activaciones.findByTokenHash(token).isEmpty()); // en la base solo está el hash

        activar(token, "corta").andExpect(status().isBadRequest());
        activar("token-falso", "contrasena-valida").andExpect(status().isBadRequest());
        activar(token, "contrasena-valida").andExpect(status().isOk());
        activar(token, "otra-contrasena-valida").andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/login").param("username", usuario).param("password", "contrasena-valida"))
                .andExpect(status().isOk());
    }

    @Test
    void generarUnEnlaceNuevoAnulaElAnteriorYNoSeGeneraConCuentaActiva() throws Exception {
        JsonNode alta = aprobar(crearSolicitud(formulario(unico())));
        long id = alta.get("id").asLong();
        String primero = generarEnlace(id).get("token").asText();
        String segundo = generarEnlace(id).get("token").asText();
        assertNotEquals(primero, segundo);

        activar(primero, "contrasena-valida").andExpect(status().isBadRequest());
        activar(segundo, "contrasena-valida").andExpect(status().isOk());

        mvc.perform(post("/api/solicitudes-alta/" + id + "/activacion").session(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void sinVeterinarioDadoDeAltaNoHayEnlace() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        long id = solicitudes.findByReferencia(referencia).orElseThrow().getId();
        mvc.perform(post("/api/solicitudes-alta/" + id + "/activacion").session(admin())).andExpect(status().isBadRequest());
    }

    // ----- El alta manual sigue funcionando -----

    @Test
    void elAltaManualSigueFuncionandoConLaMismaRegla() throws Exception {
        String marca = unico();
        var sesion = admin();
        ObjectNode clinica = mapper.createObjectNode().put("nombre", "Manual " + marca).put("direccion", "Calle 1").put("telefono", "600000001");
        String texto = mvc.perform(post("/api/clinicas").session(sesion).contentType(MediaType.APPLICATION_JSON).content(clinica.toString()))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        long clinicaId = mapper.readTree(texto).get("id").asLong();
        assertNull(clinicas.findById(clinicaId).orElseThrow().getSolicitudReferencia());

        mvc.perform(post("/api/clinicas").session(sesion).contentType(MediaType.APPLICATION_JSON).content(clinica.toString()))
                .andExpect(status().isBadRequest());
    }

    // ----- Documento acreditativo: archivos -----

    @Test
    void aceptaJpgYPngYGuardaElDocumentoConLaSolicitud() throws Exception {
        String png = crearSolicitud(formulario(unico()));
        String jpg = unico();
        enviarCon(formulario(jpg), new MockMultipartFile("documento", "acreditacion.JPEG", "image/jpeg", imagen("jpg", 500, 400)))
                .andExpect(status().isCreated());

        var documentoPng = documentos.resumenVigente(idDe(png)).orElseThrow();
        assertEquals("image/png", documentoPng.tipo());
        assertEquals("PENDIENTE", solicitudes.findByReferencia(png).orElseThrow().getDocumentoEstado().name());
        var porJpg = solicitudes.findAll().stream().filter(s -> s.getVetColegiado().equals("C" + jpg)).findFirst().orElseThrow();
        assertEquals("image/jpeg", documentos.resumenVigente(porJpg.getId()).orElseThrow().tipo());
    }

    private void rechazaArchivo(MockMultipartFile documento, String mensaje) throws Exception {
        long antes = solicitudes.count();
        String texto = enviarCon(formulario(unico()), documento).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(texto.contains(mensaje), texto);
        assertEquals(antes, solicitudes.count());
    }

    @Test
    void rechazaArchivosNoValidosSinGuardarNada() throws Exception {
        byte[] grande = new byte[6 * 1024 * 1024];
        System.arraycopy(imagen("png", 400, 300), 0, grande, 0, 100);
        rechazaArchivo(new MockMultipartFile("documento", "grande.png", "image/png", grande), "supera");
        rechazaArchivo(new MockMultipartFile("documento", "texto.png", "image/png", "no soy una imagen".getBytes()), "JPG o PNG");
        rechazaArchivo(new MockMultipartFile("documento", "dibujo.svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()), "JPG o PNG");
        rechazaArchivo(new MockMultipartFile("documento", "cambiada.png", "image/png", imagen("jpg", 400, 300)), "no coinciden");
        byte[] danada = imagen("png", 400, 300);
        java.util.Arrays.fill(danada, 30, danada.length, (byte) 7);
        rechazaArchivo(new MockMultipartFile("documento", "danada.png", "image/png", danada), "dañada");
        rechazaArchivo(new MockMultipartFile("documento", "enorme.png", "image/png", imagen("png", 9000, 300)), "demasiado grande");
        rechazaArchivo(new MockMultipartFile("documento", "diminuta.png", "image/png", imagen("png", 50, 50)), "demasiado pequeña");
    }

    @Test
    void sinArchivoONoMultipartNoSeGuardaNada() throws Exception {
        long antes = solicitudes.count();
        enviarCon(formulario(unico()), null).andExpect(status().isBadRequest());
        mvc.perform(post(URL_FORMULARIO).contentType(MediaType.APPLICATION_JSON).content(formulario(unico()).toString()))
                .andExpect(status().is4xxClientError());
        assertEquals(antes, solicitudes.count());
    }

    @Test
    void siFallaElGuardadoDelDocumentoNoQuedaLaSolicitud() throws Exception {
        String marca = unico();
        var datos = mapper.treeToValue(formulario(marca), es.florida.vet.dto.SolicitudAltaRequest.class);
        // Imagen sin contenido: la base la rechaza al guardar el documento, después de guardar la solicitud
        var rota = new es.florida.vet.entity.SolicitudDocumento.ImagenValidada("x.png", "image/png", "png", 400, 300, "0".repeat(64), null);
        long antes = solicitudes.count();
        try {
            servicio.registrar(datos, rota);
        } catch (RuntimeException esperada) {
            // El error llega al controlador como 500 y la solicitud no se presenta como recibida
        }
        assertEquals(antes, solicitudes.count());
        assertFalse(veterinarios.existsByNumeroColegiado("C" + marca));
    }

    @Test
    void elDocumentoSoloLoVeAdministracion() throws Exception {
        long id = idDe(crearSolicitud(formulario(unico())));
        String url = "/api/solicitudes-alta/" + id + "/documento";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).session(auxSanRoque())).andExpect(status().isForbidden());
        mvc.perform(get(url).session(drGarcia())).andExpect(status().isForbidden());
        var respuesta = mvc.perform(get(url).session(admin())).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("image/png", respuesta.getContentType());
        assertEquals("nosniff", respuesta.getHeader("X-Content-Type-Options"));
        assertTrue(respuesta.getHeader("Cache-Control").contains("no-store"));
        mvc.perform(multipart(url).file(documentoPrueba()).session(drGarcia())).andExpect(status().isForbidden());
    }

    // ----- Documento acreditativo: revisión manual -----

    @Test
    void noSeApruebaSinAceptarElDocumentoYSeRegistraLaRevision() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        firmada(cuerpoDecision(referencia, "APROBAR", Instant.now().getEpochSecond())).andExpect(status().isBadRequest());
        assertNull(solicitudes.findByReferencia(referencia).orElseThrow().getClinicaId());

        JsonNode aceptada = decidir(referencia, "ACEPTAR_DOCUMENTO");
        assertEquals("ACEPTADO", aceptada.get("documentoEstado").asText());
        assertEquals("Revisora Odoo", aceptada.get("documentoRevisadoPor").asText());
        assertFalse(aceptada.get("documentoFechaRevision").isNull());
        assertEquals("COMPLETADA", decidir(referencia, "APROBAR").get("estadoAlta").asText());
    }

    @Test
    void rechazarExigeMotivo() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        firmada(cuerpoDecision(referencia, "RECHAZAR", Instant.now().getEpochSecond(), " ", null)).andExpect(status().isBadRequest());
        assertEquals("PENDIENTE", solicitudes.findByReferencia(referencia).orElseThrow().getEstadoRevision().name());
    }

    @Test
    void sustituirElDocumentoAnulaLaRevisionYConservaElAnterior() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        long id = idDe(referencia);
        Long primero = documentoVigente(referencia);
        decidir(referencia, "ACEPTAR_DOCUMENTO");

        String texto = mvc.perform(multipart("/api/solicitudes-alta/" + id + "/documento")
                        .file(new MockMultipartFile("documento", "nuevo.jpg", "image/jpeg", imagen("jpg", 600, 400))).session(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode r = mapper.readTree(texto);
        assertEquals("PENDIENTE", r.get("documentoEstado").asText());
        assertTrue(r.get("documentoRevisadoPor").isNull());
        assertNotEquals(primero, r.get("documentoId").asLong());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_documentos WHERE solicitud_id = ?", Integer.class, id));

        // Aceptar el documento antiguo ya no sirve: hay que revisar el nuevo
        firmada(cuerpoDecision(referencia, "ACEPTAR_DOCUMENTO", Instant.now().getEpochSecond(), "ok", primero)).andExpect(status().isBadRequest());
        firmada(cuerpoDecision(referencia, "APROBAR", Instant.now().getEpochSecond())).andExpect(status().isBadRequest());

        aprobar(referencia);
        mvc.perform(multipart("/api/solicitudes-alta/" + id + "/documento").file(documentoPrueba()).session(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void solicitudAnteriorSinDocumentoNecesitaExcepcionConMotivo() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        long id = idDe(referencia);
        // Simula una solicitud creada antes de la migración 013
        jdbc.update("UPDATE solicitud_documentos SET vigente = FALSE WHERE solicitud_id = ?", id);
        jdbc.update("UPDATE solicitudes_alta SET documento_estado = 'SIN_DOCUMENTO' WHERE id = ?", id);

        mvc.perform(get("/api/solicitudes-alta").session(admin())).andExpect(status().isOk());
        firmada(cuerpoDecision(referencia, "APROBAR", Instant.now().getEpochSecond())).andExpect(status().isBadRequest());
        firmada(cuerpoDecision(referencia, "AUTORIZAR_EXCEPCION", Instant.now().getEpochSecond(), "", null)).andExpect(status().isBadRequest());

        JsonNode excepcion = mapper.readTree(firmada(cuerpoDecision(referencia, "AUTORIZAR_EXCEPCION", Instant.now().getEpochSecond(),
                "Autorizado por dirección: cliente conocido", null)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("EXCEPCION", excepcion.get("documentoEstado").asText());
        assertEquals("Autorizado por dirección: cliente conocido", excepcion.get("documentoObservaciones").asText());
        assertEquals("COMPLETADA", decidir(referencia, "APROBAR").get("estadoAlta").asText());
    }

    // ----- Documento acreditativo: Odoo -----

    @Test
    void elDocumentoLlegaAOdooUnaSolaVezYUnFalloQuedaPendiente() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        long documentoId = documentoVigente(referencia);

        doReturn(OdooConnector.Resultado.fallo("Odoo no responde")).when(odoo).adjuntarDocumentoSolicitud(anyInt(), eq(documentoId), any(), any(), any());
        try {
            servicio.enviarPendientesAOdoo();
        } finally {
            doCallRealMethod().when(odoo).adjuntarDocumentoSolicitud(anyInt(), any(Long.class), any(), any(), any());
        }
        var conFallo = solicitudes.findByReferencia(referencia).orElseThrow();
        assertTrue(conFallo.isOdooPendiente());
        assertNotNull(conFallo.getOdooId());
        assertTrue(conFallo.getOdooUltimoError().contains("Documento"));
        assertNull(documentos.resumenVigente(conFallo.getId()).orElseThrow().odooAdjuntoId());

        servicio.enviarPendientesAOdoo();
        servicio.enviarPendientesAOdoo();
        var enviada = solicitudes.findByReferencia(referencia).orElseThrow();
        assertFalse(enviada.isOdooPendiente());
        assertNotNull(documentos.resumenVigente(enviada.getId()).orElseThrow().odooAdjuntoId());
        // Una llamada fallida y una correcta: la tercera pasada ya no lo vuelve a enviar
        verify(odoo, times(2)).adjuntarDocumentoSolicitud(anyInt(), eq(documentoId), any(), any(), any());
    }
}
