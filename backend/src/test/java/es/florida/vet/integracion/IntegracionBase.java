package es.florida.vet.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integración: arranca la aplicación completa
 * (seguridad, controladores, servicios y MySQL real) y hace peticiones HTTP
 * simuladas con MockMvc, igual que haría el frontend.
 *
 * Si MySQL no está disponible las pruebas se OMITEN (no fallan): así
 * "mvnw test" funciona también en un equipo sin base de datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegracionBase {

    static final String SECRETO_WEBHOOK = "secreto-solo-para-pruebas";
    static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Autowired
    protected MockMvc mvc;

    protected final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void prepararBaseDeDatos() {
        assumeTrue(BaseDatosPruebas.disponible(), "MySQL no disponible en " + BaseDatosPruebas.URL);
        BaseDatosPruebas.recargar();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> BaseDatosPruebas.URL);
        registro.add("spring.datasource.username", () -> BaseDatosPruebas.USUARIO);
        registro.add("spring.datasource.password", () -> BaseDatosPruebas.CLAVE);
        registro.add("vetstock.pagos.secreto-webhook", () -> SECRETO_WEBHOOK);
        registro.add("vetstock.odoo.modo", () -> "SIMULADO");
        // La tarea de caducidad se lanza a mano en su prueba
        registro.add("vetstock.reservas.revision-ms", () -> "86400000");
    }

    // ----- Sesión -----

    protected MockHttpSession login(String usuario, String clave) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/auth/login").param("username", usuario).param("password", clave))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    protected MockHttpSession admin() throws Exception { return login("admin", "admin123"); }
    protected MockHttpSession auxSanRoque() throws Exception { return login("aux.sanroque", "aux123"); }
    protected MockHttpSession auxMediterraneo() throws Exception { return login("aux.mediterraneo", "aux123"); }
    protected MockHttpSession drGarcia() throws Exception { return login("dr.garcia", "vet123"); }
    protected MockHttpSession draLopez() throws Exception { return login("dra.lopez", "vet123"); }
    protected MockHttpSession drMartin() throws Exception { return login("dr.martin", "vet123"); }

    // ----- Peticiones -----

    protected ResultActions getJson(MockHttpSession sesion, String url) throws Exception {
        return mvc.perform(get(url).session(sesion));
    }

    protected ResultActions postJson(MockHttpSession sesion, String url, Object cuerpo) throws Exception {
        return mvc.perform(post(url).session(sesion).contentType(MediaType.APPLICATION_JSON).content(aJson(cuerpo)));
    }

    protected ResultActions putJson(MockHttpSession sesion, String url, Object cuerpo) throws Exception {
        return mvc.perform(put(url).session(sesion).contentType(MediaType.APPLICATION_JSON).content(aJson(cuerpo)));
    }

    protected ResultActions patchJson(MockHttpSession sesion, String url, Object cuerpo) throws Exception {
        var peticion = patch(url).session(sesion);
        if (cuerpo != null) peticion.contentType(MediaType.APPLICATION_JSON).content(aJson(cuerpo));
        return mvc.perform(peticion);
    }

    protected JsonNode json(ResultActions acciones) throws Exception {
        return mapper.readTree(acciones.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String aJson(Object cuerpo) throws Exception {
        return cuerpo instanceof String texto ? texto : mapper.writeValueAsString(cuerpo);
    }

    // ----- Datos de negocio -----

    /** Crea un pedido en BORRADOR con una línea y devuelve su id. */
    protected long crearPedido(MockHttpSession sesion, long productoId, int cantidad) throws Exception {
        String cuerpo = "{\"observaciones\":\"Prueba de integración\",\"lineas\":[{\"productoId\":"
                + productoId + ",\"cantidad\":" + cantidad + "}]}";
        return json(postJson(sesion, "/api/pedidos", cuerpo).andExpect(status().isCreated())).get("id").asLong();
    }

    protected String firmaDibujo() {
        return "{\"tipoFirma\":\"DIBUJO\",\"imagenBase64\":\"data:image/png;base64," + PNG_1X1 + "\",\"confirmacion\":true}";
    }

    protected JsonNode producto(long id) throws Exception {
        return json(getJson(admin(), "/api/productos/" + id).andExpect(status().isOk()));
    }

    /** Envía al webhook una notificación firmada como lo haría el proveedor. */
    protected ResultActions notificarPago(String referencia, String importe, String moneda, String resultado)
            throws Exception {
        String cuerpo = "{\"referencia\":\"" + referencia + "\",\"importe\":" + importe
                + ",\"moneda\":\"" + moneda + "\",\"resultado\":\"" + resultado + "\"}";
        return notificarPagoConFirma(cuerpo, hmac(cuerpo));
    }

    protected ResultActions notificarPagoConFirma(String cuerpo, String firma) throws Exception {
        var peticion = post("/api/pagos/notificacion").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (firma != null) peticion.header("X-Firma-Notificacion", firma);
        return mvc.perform(peticion);
    }

    protected static String hmac(String cuerpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRETO_WEBHOOK.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
    }
}
