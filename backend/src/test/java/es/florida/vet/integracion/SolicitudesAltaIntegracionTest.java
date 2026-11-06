package es.florida.vet.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.florida.vet.odoo.OdooConnector;
import es.florida.vet.repository.ActivacionCuentaRepository;
import es.florida.vet.repository.ClinicaRepository;
import es.florida.vet.repository.SolicitudAltaRepository;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    private ResultActions enviar(ObjectNode datos) throws Exception {
        return mvc.perform(post(URL_FORMULARIO).contentType(MediaType.APPLICATION_JSON).content(datos.toString()));
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
        return mapper.createObjectNode().put("referencia", referencia).put("decision", tipo)
                .put("revisor", "Revisora Odoo").put("observaciones", "ok").put("marcaTiempo", marcaTiempo).toString();
    }

    private JsonNode decidir(String referencia, String tipo) throws Exception {
        String cuerpo = cuerpoDecision(referencia, tipo, Instant.now().getEpochSecond());
        String texto = decision(cuerpo, FirmaHmac.calcular(SECRETO, cuerpo)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(texto);
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
            var tareas = java.util.Collections.nCopies(4, (java.util.concurrent.Callable<String>) () -> servicio.registrar(datos));
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
        assertEquals("COMPLETADA", decidir(referencia, "APROBAR").get("estadoAlta").asText());
    }

    @Test
    void aprobarCreaClinicaYVeterinarioSinAccesoYRegistraIds() throws Exception {
        String marca = unico();
        String referencia = crearSolicitud(formulario(marca));
        JsonNode r = decidir(referencia, "APROBAR");

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
        JsonNode primera = decidir(referencia, "APROBAR");
        long clinicasTrasPrimera = clinicas.count();
        long veterinariosTrasPrimera = veterinarios.count();

        JsonNode segunda = decidir(referencia, "APROBAR");

        assertEquals(primera.get("clinicaId").asLong(), segunda.get("clinicaId").asLong());
        assertEquals(primera.get("veterinarioId").asLong(), segunda.get("veterinarioId").asLong());
        assertEquals(primera.get("fechaRevision").asText(), segunda.get("fechaRevision").asText());
        assertEquals(clinicasTrasPrimera, clinicas.count());
        assertEquals(veterinariosTrasPrimera, veterinarios.count());
    }

    @Test
    void laClinicaDadaDeAltaNoSePuedeRechazar() throws Exception {
        String referencia = crearSolicitud(formulario(unico()));
        decidir(referencia, "APROBAR");
        String cuerpo = cuerpoDecision(referencia, "RECHAZAR", Instant.now().getEpochSecond());
        decision(cuerpo, FirmaHmac.calcular(SECRETO, cuerpo)).andExpect(status().isBadRequest());
        assertEquals("APROBADA", solicitudes.findByReferencia(referencia).orElseThrow().getEstadoRevision().name());
    }

    // ----- Duplicados -----

    @Test
    void mismoFiscalConOtroVeterinarioPasaARevisionSinTocarLaClinica() throws Exception {
        String marca = unico();
        JsonNode original = decidir(crearSolicitud(formulario(marca)), "APROBAR");
        Long responsable = clinicas.findById(original.get("clinicaId").asLong()).orElseThrow().getVeterinarioResponsable().getId();

        ObjectNode otra = formulario(unico());
        otra.put("identificadorFiscal", "F" + marca);
        long antes = clinicas.count();
        JsonNode r = decidir(crearSolicitud(otra), "APROBAR");

        assertEquals("REQUIERE_REVISION", r.get("estadoAlta").asText());
        assertTrue(r.get("clinicaId").isNull());
        assertEquals(antes, clinicas.count());
        assertEquals(responsable, clinicas.findById(original.get("clinicaId").asLong()).orElseThrow().getVeterinarioResponsable().getId());
    }

    @Test
    void mismoFiscalYMismoColegiadoSeVinculaSinDuplicar() throws Exception {
        String marca = unico();
        JsonNode original = decidir(crearSolicitud(formulario(marca)), "APROBAR");

        ObjectNode otra = formulario(marca);
        otra.put("email", "otro" + marca.toLowerCase() + "@ejemplo.test"); // otra huella: no es un doble clic
        otra.put("telefono", "611111111");
        long antes = clinicas.count();
        JsonNode r = decidir(crearSolicitud(otra), "APROBAR");

        assertEquals("COMPLETADA", r.get("estadoAlta").asText());
        assertEquals(original.get("clinicaId").asLong(), r.get("clinicaId").asLong());
        assertEquals(antes, clinicas.count());
        assertEquals("600000000", clinicas.findById(r.get("clinicaId").asLong()).orElseThrow().getTelefono());
    }

    @Test
    void colegiadoEnOtraClinicaONombreIgualPasanARevision() throws Exception {
        String marca = unico();
        decidir(crearSolicitud(formulario(marca)), "APROBAR");

        ObjectNode mismoColegiado = formulario(unico());
        mismoColegiado.put("vetColegiado", "C" + marca);
        assertEquals("REQUIERE_REVISION", decidir(crearSolicitud(mismoColegiado), "APROBAR").get("estadoAlta").asText());

        ObjectNode mismoNombre = formulario(unico());
        mismoNombre.put("clinicaNombre", ("Clínica Prueba " + marca).toUpperCase());
        assertEquals("REQUIERE_REVISION", decidir(crearSolicitud(mismoNombre), "APROBAR").get("estadoAlta").asText());
    }

    // ----- Fallos de Odoo -----

    @Test
    void siOdooFallaLaSolicitudSeConservaYElReintentoNoDuplica() throws Exception {
        String marca = unico();
        String referencia = crearSolicitud(formulario(marca));
        doReturn(OdooConnector.Resultado.fallo("Odoo no responde")).when(odoo).guardarContactoVeterinario(any());
        JsonNode fallida;
        try {
            fallida = decidir(referencia, "APROBAR");
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
        JsonNode alta = decidir(crearSolicitud(formulario(marca)), "APROBAR");
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
        JsonNode alta = decidir(crearSolicitud(formulario(unico())), "APROBAR");
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
}
