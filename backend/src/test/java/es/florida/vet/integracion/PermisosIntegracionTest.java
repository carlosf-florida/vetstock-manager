package es.florida.vet.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permisos comprobados en el backend: sesión, roles y separación entre clínicas
 * (pedidos, firmas y documentos). En los datos iniciales el pedido 2 es de la
 * Clínica San Roque y aux.mediterraneo pertenece a otra clínica.
 */
class PermisosIntegracionTest extends IntegracionBase {

    private static final long PEDIDO_SAN_ROQUE = 2L;

    @Test
    @DisplayName("inicio y cierre de sesión")
    void loginYLogout() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "incorrecta"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        MockHttpSession sesion = auxSanRoque();
        getJson(sesion, "/api/auth/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("aux.sanroque"))
                .andExpect(jsonPath("$.rol").value("AUXILIAR"));

        mvc.perform(post("/api/auth/logout").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(sesion)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un usuario de otra clínica no ve ni opera pedidos ajenos")
    void separacionEntreClinicas() throws Exception {
        MockHttpSession otra = auxMediterraneo();
        getJson(otra, "/api/pedidos/" + PEDIDO_SAN_ROQUE).andExpect(status().isForbidden());
        getJson(otra, "/api/pedidos/" + PEDIDO_SAN_ROQUE + "/firma").andExpect(status().isForbidden());
        getJson(otra, "/api/pagos?pedidoId=" + PEDIDO_SAN_ROQUE).andExpect(status().isForbidden());
        patchJson(otra, "/api/pedidos/" + PEDIDO_SAN_ROQUE + "/cancelar", null).andExpect(status().isForbidden());
        patchJson(drMartin(), "/api/pedidos/" + PEDIDO_SAN_ROQUE + "/firmar", firmaDibujo()).andExpect(status().isForbidden());

        JsonNode lista = json(getJson(otra, "/api/pedidos").andExpect(status().isOk()));
        lista.forEach(p -> assertFalse(p.get("id").asLong() == PEDIDO_SAN_ROQUE, "ve un pedido de otra clínica"));
        JsonNode filtrada = json(getJson(otra, "/api/pedidos?clinicaId=1").andExpect(status().isOk()));
        assertTrue(filtrada.isEmpty());

        // Las rutas de administración están cerradas para los clientes
        getJson(otra, "/api/informes/pendientes").andExpect(status().isForbidden());
        getJson(otra, "/api/clinicas").andExpect(status().isForbidden());
        postJson(otra, "/api/bultos", "{\"identificador\":\"X\",\"pedidoId\":2}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("documentos: solo administración sube PDF; solo la clínica del pedido los descarga")
    void documentosConPermisos() throws Exception {
        MockHttpSession admin = admin();
        MockMultipartFile pdf = new MockMultipartFile("archivo", "albaran-prueba.pdf", "application/pdf",
                "%PDF-1.4\n% Documento de PRUEBA sin validez\n".getBytes(StandardCharsets.UTF_8));
        String url = "/api/pedidos/" + PEDIDO_SAN_ROQUE + "/documentos";

        long documento = json(mvc.perform(multipart(url).file(pdf).param("tipo", "ALBARAN").session(admin))
                .andExpect(status().isCreated())).get("id").asLong();

        MockMultipartFile noPdf = new MockMultipartFile("archivo", "virus.pdf", "application/pdf", "MZ-ejecutable".getBytes());
        mvc.perform(multipart(url).file(noPdf).param("tipo", "ALBARAN").session(admin)).andExpect(status().isBadRequest());
        byte[] enorme = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-".getBytes(), 0, enorme, 0, 5);
        mvc.perform(multipart(url).file(new MockMultipartFile("archivo", "grande.pdf", "application/pdf", enorme))
                .param("tipo", "ALBARAN").session(admin)).andExpect(status().isBadRequest());

        // Un cliente no puede subir documentos
        mvc.perform(multipart(url).file(pdf).param("tipo", "ALBARAN").session(auxSanRoque())).andExpect(status().isForbidden());

        getJson(auxSanRoque(), "/api/documentos/" + documento + "/descargar").andExpect(status().isOk());
        getJson(auxMediterraneo(), "/api/documentos/" + documento + "/descargar").andExpect(status().isForbidden());
        getJson(auxMediterraneo(), url).andExpect(status().isForbidden());
    }
}
