package es.florida.vet.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Salidas de expedición (tabla destinos con código), salida habitual de la clínica,
 * asignación al preparar y bultos por pedido. Datos iniciales: Regional tiene el código 14
 * y es la salida habitual de la Clínica Veterinaria San Roque (id 1); el Hospital
 * Veterinario Mediterráneo (id 3) no tiene salida habitual.
 */
class LogisticaSalidasIntegracionTest extends IntegracionBase {

    /** Pedido de San Roque firmado por el Dr. García, pagado y en preparación. */
    private long pedidoEnPreparacion(MockHttpSession cliente, MockHttpSession veterinario, long productoId) throws Exception {
        long id = crearPedido(cliente, productoId, 1);
        patchJson(cliente, "/api/pedidos/" + id + "/enviar-firma", null).andExpect(status().isOk());
        patchJson(veterinario, "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isOk());
        JsonNode pago = json(postJson(cliente, "/api/pagos/" + id + "/iniciar", "{}").andExpect(status().isCreated()));
        notificarPago(pago.get("referencia").asText(), pago.get("importe").asText(), "EUR", "CONFIRMADO")
                .andExpect(status().isOk());
        patchJson(admin(), "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isOk());
        return id;
    }

    private void bulto(long pedidoId, String identificador) throws Exception {
        postJson(admin(), "/api/bultos", "{\"identificador\":\"" + identificador + "\",\"pedidoId\":" + pedidoId + "}")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("configurar 14 - Regional en San Roque; al preparar el pedido toma esa salida; los bultos se cuentan")
    void salidaHabitualYRecuentoDeBultos() throws Exception {
        MockHttpSession admin = admin();
        putJson(admin, "/api/destinos/3", "{\"codigo\":\"14\",\"nombre\":\"Regional\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.etiqueta").value("14 - Regional"));
        putJson(admin, "/api/clinicas/1/salida-habitual", "{\"destinoId\":3}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.destinoHabitualEtiqueta").value("14 - Regional"));
        // Un código ya usado por otra salida se rechaza
        putJson(admin, "/api/destinos/1", "{\"codigo\":\"14\",\"nombre\":\"Valencia\"}").andExpect(status().isBadRequest());

        long id = pedidoEnPreparacion(auxSanRoque(), drGarcia(), 5);
        bulto(id, "LS-A-" + id);
        bulto(id, "LS-B-" + id);
        postJson(admin, "/api/bultos", "{\"identificador\":\"LS-A-" + id + "\",\"pedidoId\":" + id + "}")
                .andExpect(status().isBadRequest()); // identificador repetido

        getJson(admin, "/api/pedidos/" + id).andExpect(jsonPath("$.bultos").value(2)).andExpect(jsonPath("$.salida").doesNotExist());
        JsonNode lista = json(getJson(admin, "/api/pedidos"));
        lista.forEach(p -> { if (p.get("id").asLong() == id) assertEquals(2, p.get("bultos").asInt()); });
        assertEquals(2, json(getJson(admin, "/api/bultos?pedidoId=" + id)).size());

        patchJson(admin, "/api/pedidos/" + id + "/preparar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.salida").value("14 - Regional"))
                .andExpect(jsonPath("$.salidaManual").value(false))
                .andExpect(jsonPath("$.salidaAsignadaPor").value("Laura Sánchez (Almacén)"));
        // Repetir la operación no cambia nada (transición no permitida)
        patchJson(admin, "/api/pedidos/" + id + "/preparar", null).andExpect(status().isBadRequest());

        bulto(id, "LS-C-" + id);
        getJson(admin, "/api/pedidos/" + id).andExpect(jsonPath("$.bultos").value(3));

        // Cambiar la salida habitual de la clínica NO cambia los pedidos ya preparados
        putJson(admin, "/api/clinicas/1/salida-habitual", "{\"destinoId\":2}").andExpect(status().isOk());
        getJson(admin, "/api/pedidos/" + id).andExpect(jsonPath("$.salida").value("14 - Regional"));
        putJson(admin, "/api/clinicas/1/salida-habitual", "{\"destinoId\":3}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("una salida elegida por administración se mantiene al preparar")
    void salidaManualSeRespeta() throws Exception {
        long id = pedidoEnPreparacion(auxSanRoque(), drGarcia(), 6);
        bulto(id, "LS-M-" + id);
        patchJson(admin(), "/api/pedidos/" + id + "/salida", "{\"destinoId\":1}").andExpect(status().isOk())
                .andExpect(jsonPath("$.salida").value("Valencia")).andExpect(jsonPath("$.salidaManual").value(true));
        patchJson(admin(), "/api/pedidos/" + id + "/preparar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.salida").value("Valencia"));
    }

    @Test
    @DisplayName("clínica sin salida habitual: no se prepara hasta elegir una salida; nunca una arbitraria")
    void clinicaSinSalida() throws Exception {
        long id = pedidoEnPreparacion(auxMediterraneo(), drMartin(), 9);
        bulto(id, "LS-S-" + id);
        String mensaje = json(patchJson(admin(), "/api/pedidos/" + id + "/preparar", null)
                .andExpect(status().isBadRequest())).get("mensaje").asText();
        assertTrue(mensaje.contains("no tiene salida habitual"), mensaje);
        getJson(admin(), "/api/pedidos/" + id).andExpect(jsonPath("$.estado").value("EN_PREPARACION"))
                .andExpect(jsonPath("$.salida").doesNotExist());

        patchJson(admin(), "/api/pedidos/" + id + "/salida", "{\"destinoId\":3}").andExpect(status().isOk());
        patchJson(admin(), "/api/pedidos/" + id + "/preparar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.salida").value("14 - Regional"));
    }

    @Test
    @DisplayName("cambio de salida: solo admin, no con bultos en un palet de otra salida, nunca expedido")
    void cambioDeSalidaConReglas() throws Exception {
        MockHttpSession admin = admin();
        long id = pedidoEnPreparacion(auxSanRoque(), drGarcia(), 10);
        bulto(id, "LS-P-" + id);
        patchJson(admin, "/api/pedidos/" + id + "/preparar", null).andExpect(status().isOk());
        long palet = json(postJson(admin, "/api/palets", "{\"identificador\":\"LS-PAL-" + id + "\",\"destinoId\":3}")).get("id").asLong();
        long bultoId = json(getJson(admin, "/api/bultos?pedidoId=" + id)).get(0).get("id").asLong();
        patchJson(admin, "/api/bultos/" + bultoId + "/asignar-palet", "{\"paletId\":" + palet + "}").andExpect(status().isOk());
        // El mismo bulto otra vez al mismo palet: sin duplicados
        patchJson(admin, "/api/bultos/" + bultoId + "/asignar-palet", "{\"paletId\":" + palet + "}").andExpect(status().isOk());
        assertEquals(1, json(getJson(admin, "/api/palets/" + palet)).get("bultos").size());

        patchJson(auxSanRoque(), "/api/pedidos/" + id + "/salida", "{\"destinoId\":1}").andExpect(status().isForbidden());
        patchJson(admin, "/api/pedidos/" + id + "/salida", "{\"destinoId\":1}").andExpect(status().isBadRequest());

        patchJson(admin, "/api/pedidos/" + id + "/expedir", null).andExpect(status().isOk());
        patchJson(admin, "/api/pedidos/" + id + "/salida", "{\"destinoId\":3}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("permisos: los clientes no gestionan logística ni ven bultos de otras clínicas")
    void permisos() throws Exception {
        getJson(auxSanRoque(), "/api/bultos").andExpect(status().isForbidden());
        getJson(auxSanRoque(), "/api/destinos").andExpect(status().isForbidden());
        putJson(auxSanRoque(), "/api/clinicas/1/salida-habitual", "{\"destinoId\":3}").andExpect(status().isForbidden());
        putJson(drGarcia(), "/api/destinos/3", "{\"codigo\":\"99\",\"nombre\":\"X\"}").andExpect(status().isForbidden());
        JsonNode propios = json(getJson(auxMediterraneo(), "/api/pedidos"));
        propios.forEach(p -> assertFalse(p.get("clinicaId").asLong() == 1L, "ve pedidos de otra clínica"));
    }
}
