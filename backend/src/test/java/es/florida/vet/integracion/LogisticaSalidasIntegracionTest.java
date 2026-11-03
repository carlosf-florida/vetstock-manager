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

    /** Pedido firmado por el veterinario asignado, pagado y en preparación (1 unidad). */
    private long pedidoEnPreparacion(MockHttpSession cliente, MockHttpSession veterinario, long productoId) throws Exception {
        return pedidoEnPreparacion(cliente, veterinario, productoId, 1);
    }

    private long pedidoEnPreparacion(MockHttpSession cliente, MockHttpSession veterinario, long productoId,
                                     int cantidad) throws Exception {
        long id = crearPedido(cliente, productoId, cantidad);
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

        // 3 sacos × 12,5 kg = 37,5 kg → 2 bultos
        long id = pedidoEnPreparacion(auxSanRoque(), drGarcia(), 11, 3);
        bulto(id, "LS-A-" + id);
        postJson(admin, "/api/bultos", "{\"identificador\":\"LS-A-" + id + "\",\"pedidoId\":" + id + "}")
                .andExpect(status().isBadRequest()); // identificador repetido

        getJson(admin, "/api/pedidos/" + id).andExpect(jsonPath("$.bultos").value(1))
                .andExpect(jsonPath("$.bultosCalculados").value(2)).andExpect(jsonPath("$.pesoTotalKg").value(37.5))
                .andExpect(jsonPath("$.salida").doesNotExist());
        JsonNode lista = json(getJson(admin, "/api/pedidos"));
        lista.forEach(p -> { if (p.get("id").asLong() == id) assertEquals(2, p.get("bultosCalculados").asInt()); });

        // Al preparar se crea el bulto que falta (VSM-{id}-B1), sin tocar el que ya había
        patchJson(admin, "/api/pedidos/" + id + "/preparar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.salida").value("14 - Regional"))
                .andExpect(jsonPath("$.salidaManual").value(false))
                .andExpect(jsonPath("$.salidaAsignadaPor").value("Laura Sánchez (Almacén)"))
                .andExpect(jsonPath("$.bultos").value(2)).andExpect(jsonPath("$.bultosCalculados").value(2));
        JsonNode registros = json(getJson(admin, "/api/bultos?pedidoId=" + id));
        assertEquals(2, registros.size());
        assertTrue(registros.toString().contains("LS-A-" + id) && registros.toString().contains("VSM-" + id + "-B1"));
        // Repetir la operación no cambia nada (transición no permitida) ni crea bultos
        patchJson(admin, "/api/pedidos/" + id + "/preparar", null).andExpect(status().isBadRequest());
        assertEquals(2, json(getJson(admin, "/api/bultos?pedidoId=" + id)).size());
        // No se puede registrar a mano un bulto por encima de los calculados
        postJson(admin, "/api/bultos", "{\"identificador\":\"LS-C-" + id + "\",\"pedidoId\":" + id + "}")
                .andExpect(status().isBadRequest());
        getJson(admin, "/api/pedidos/" + id).andExpect(jsonPath("$.bultos").value(2));
        // La clínica no ve la logística interna de su pedido
        getJson(drGarcia(), "/api/pedidos/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.salida").doesNotExist()).andExpect(jsonPath("$.bultos").doesNotExist())
                .andExpect(jsonPath("$.salidaAsignadaPor").doesNotExist());
        JsonNode listaClinica = json(getJson(auxSanRoque(), "/api/pedidos"));
        listaClinica.forEach(p -> assertTrue(!p.hasNonNull("salida") && !p.hasNonNull("bultos"), "la clínica ve la salida o los bultos"));

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
        // Un bulto que ya está en un palet no se elimina
        deleteJson(admin, "/api/bultos/" + bultoId).andExpect(status().isBadRequest());

        patchJson(auxSanRoque(), "/api/pedidos/" + id + "/salida", "{\"destinoId\":1}").andExpect(status().isForbidden());
        patchJson(admin, "/api/pedidos/" + id + "/salida", "{\"destinoId\":1}").andExpect(status().isBadRequest());

        patchJson(admin, "/api/pedidos/" + id + "/expedir", null).andExpect(status().isOk());
        patchJson(admin, "/api/pedidos/" + id + "/salida", "{\"destinoId\":3}").andExpect(status().isBadRequest());
    }

    /** Cambia el peso por unidad de un producto conservando el resto de sus datos. */
    private void pesoProducto(long productoId, String pesoKg) throws Exception {
        JsonNode p = producto(productoId);
        String cuerpo = "{\"codigo\":\"" + p.get("codigo").asText() + "\",\"nombre\":\"" + p.get("nombre").asText()
                + "\",\"descripcion\":\"" + p.get("descripcion").asText() + "\",\"categoria\":\"" + p.get("categoria").asText()
                + "\",\"precio\":" + p.get("precio").asText() + ",\"pesoUnitarioKg\":" + pesoKg
                + ",\"stockInicial\":0,\"stockMinimo\":" + p.get("stockMinimo").asInt() + "}";
        putJson(admin(), "/api/productos/" + productoId, cuerpo).andExpect(status().isOk());
    }

    @Test
    @DisplayName("bultos por peso: 25 kg exactos = 1 bulto creado al preparar; sin peso no se prepara")
    void bultosPorPeso() throws Exception {
        MockHttpSession admin = admin();
        // 2 sacos × 12,5 kg = 25 kg exactos → 1 bulto, creado solo al preparar
        long id = pedidoEnPreparacion(auxSanRoque(), drGarcia(), 11, 2);
        patchJson(admin, "/api/pedidos/" + id + "/preparar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.bultos").value(1)).andExpect(jsonPath("$.bultosCalculados").value(1));
        assertEquals("VSM-" + id + "-B1", json(getJson(admin, "/api/bultos?pedidoId=" + id)).get(0).get("identificador").asText());

        // Producto sin peso: cálculo incompleto, no se registran bultos ni se prepara
        pesoProducto(4, "null");
        try {
            long sinPeso = pedidoEnPreparacion(auxSanRoque(), drGarcia(), 4, 2);
            JsonNode pedido = json(getJson(admin, "/api/pedidos/" + sinPeso));
            assertTrue(pedido.get("bultosCalculados").isNull());
            assertEquals("Vacuna antirrábica", pedido.get("productosSinPeso").get(0).asText());
            postJson(admin, "/api/bultos", "{\"identificador\":\"LS-SP-" + sinPeso + "\",\"pedidoId\":" + sinPeso + "}")
                    .andExpect(status().isBadRequest());
            String mensaje = json(patchJson(admin, "/api/pedidos/" + sinPeso + "/preparar", null)
                    .andExpect(status().isBadRequest())).get("mensaje").asText();
            assertTrue(mensaje.contains("falta el peso"), mensaje);
            assertEquals(0, json(getJson(admin, "/api/bultos?pedidoId=" + sinPeso)).size());
        } finally {
            pesoProducto(4, "0.020");
        }
    }

    @Test
    @DisplayName("sobran bultos tras cambiar un peso: no se prepara ni se borra nada solo; admin elimina el sobrante")
    void bultosSobrantes() throws Exception {
        MockHttpSession admin = admin();
        // 3 sacos × 12,5 kg = 37,5 kg → 2 bultos; se registran los 2 a mano
        long id = pedidoEnPreparacion(auxSanRoque(), drGarcia(), 11, 3);
        bulto(id, "LS-X1-" + id);
        bulto(id, "LS-X2-" + id);
        pesoProducto(11, "8.000");   // ahora 24 kg → 1 bulto
        try {
            String mensaje = json(patchJson(admin, "/api/pedidos/" + id + "/preparar", null)
                    .andExpect(status().isBadRequest())).get("mensaje").asText();
            assertTrue(mensaje.contains("elimina los sobrantes"), mensaje);
            assertEquals(2, json(getJson(admin, "/api/bultos?pedidoId=" + id)).size());

            long sobrante = json(getJson(admin, "/api/bultos?pedidoId=" + id)).get(0).get("id").asLong();
            deleteJson(admin, "/api/bultos/" + sobrante).andExpect(status().isNoContent());
            long ultimo = json(getJson(admin, "/api/bultos?pedidoId=" + id)).get(0).get("id").asLong();
            deleteJson(admin, "/api/bultos/" + ultimo).andExpect(status().isBadRequest()); // ya no sobra
            deleteJson(auxSanRoque(), "/api/bultos/" + ultimo).andExpect(status().isForbidden());
            patchJson(admin, "/api/pedidos/" + id + "/preparar", null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.bultos").value(1)).andExpect(jsonPath("$.bultosCalculados").value(1));
        } finally {
            pesoProducto(11, "12.500");
        }
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
