package es.florida.vet.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Separación «Pagados pendientes de firma» / «Firmados y pagados» / «Otros» y
 * condición para preparar: firma VÁLIDA del veterinario asignado + pago completo.
 * Algunos casos (firma de otra persona, contenido cambiado, pago parcial) no se
 * pueden provocar desde la API, así que se preparan directamente en la base de pruebas.
 */
class ClasificacionFirmaPagoIntegracionTest extends IntegracionBase {

    @Autowired
    private JdbcTemplate jdbc;

    private long pedidoEnviadoAFirma(MockHttpSession aux, long productoId) throws Exception {
        long id = crearPedido(aux, productoId, 1);
        patchJson(aux, "/api/pedidos/" + id + "/enviar-firma", null).andExpect(status().isOk());
        return id;
    }

    private void pagar(MockHttpSession sesion, long id) throws Exception {
        JsonNode pago = json(postJson(sesion, "/api/pagos/" + id + "/iniciar", "{}").andExpect(status().isCreated()));
        notificarPago(pago.get("referencia").asText(), pago.get("importe").asText(), "EUR", "CONFIRMADO")
                .andExpect(status().isOk());
    }

    private JsonNode autorizacion(MockHttpSession sesion, long id) throws Exception {
        return json(getJson(sesion, "/api/pedidos/" + id).andExpect(status().isOk())).get("autorizacion");
    }

    @Test
    @DisplayName("pagado sin firma: «pagados pendientes de firma», no se prepara; al firmar pasa a «firmados y pagados» sin duplicarse")
    void pagadoSinFirmaYDespuesFirmado() throws Exception {
        MockHttpSession aux = auxSanRoque();
        MockHttpSession admin = admin();
        long id = pedidoEnviadoAFirma(aux, 5);
        pagar(aux, id);

        JsonNode a = autorizacion(admin, id);
        assertEquals("PAGADO_PENDIENTE_FIRMA", a.get("clasificacion").asText());
        assertEquals("Falta la firma de Dr. Antonio García", a.get("motivoFirma").asText());
        assertTrue(a.get("pagoCompleto").asBoolean());
        getJson(admin, "/api/pedidos/" + id).andExpect(jsonPath("$.estado").value("PENDIENTE_FIRMA"));
        patchJson(admin, "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isBadRequest());

        int pedidosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM pedidos", Integer.class);
        patchJson(drGarcia(), "/api/pedidos/" + id + "/firmar", firmaDibujo())
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("LISTO_PREPARAR"));
        JsonNode b = autorizacion(admin, id);
        assertEquals("FIRMADO_Y_PAGADO", b.get("clasificacion").asText());
        assertTrue(b.get("listoParaPreparar").asBoolean());
        assertEquals(pedidosAntes, jdbc.queryForObject("SELECT COUNT(*) FROM pedidos", Integer.class));

        // En preparación sigue «firmado y pagado», pero ya no aparece como listo para preparar
        patchJson(admin, "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isOk());
        JsonNode c = autorizacion(admin, id);
        assertEquals("FIRMADO_Y_PAGADO", c.get("clasificacion").asText());
        assertFalse(c.get("listoParaPreparar").asBoolean());
    }

    @Test
    @DisplayName("firmado sin pago: «otros pendientes» y no se puede preparar")
    void firmadoSinPago() throws Exception {
        long id = pedidoEnviadoAFirma(auxSanRoque(), 5);
        patchJson(drGarcia(), "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isOk());

        JsonNode a = autorizacion(admin(), id);
        assertEquals("OTROS", a.get("clasificacion").asText());
        assertTrue(a.get("firmaValida").asBoolean());
        assertFalse(a.get("pagoCompleto").asBoolean());
        assertFalse(a.get("listoParaPreparar").asBoolean());
        patchJson(admin(), "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un pago por menos del total no cuenta como pagado")
    void pagoParcialNoCuenta() throws Exception {
        long id = pedidoEnviadoAFirma(auxSanRoque(), 6);
        patchJson(drGarcia(), "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isOk());
        // Caso imposible por la API (la notificación exige el total exacto): se fuerza en la base
        jdbc.update("INSERT INTO pagos (pedido_id, proveedor, referencia, estado, importe, moneda, token_notificacion, intentos, fecha_inicio, fecha_resultado) "
                + "VALUES (?, 'SIMULADO', ?, 'CONFIRMADO', 1.00, 'EUR', 't', 1, NOW(), NOW())", id, "PARCIAL-" + id);
        jdbc.update("UPDATE pedidos SET fecha_pago = NOW(), estado = 'LISTO_PREPARAR' WHERE id = ?", id);

        JsonNode a = autorizacion(admin(), id);
        assertFalse(a.get("pagoCompleto").asBoolean());
        assertTrue(a.get("importePendiente").decimalValue().signum() > 0);
        assertEquals("OTROS", a.get("clasificacion").asText());
        patchJson(admin(), "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("firma de otra persona o de un contenido anterior: no habilita preparación; el asignado puede volver a firmar")
    void firmaNoValida() throws Exception {
        MockHttpSession aux = auxSanRoque();
        long id = pedidoEnviadoAFirma(aux, 5);
        patchJson(drGarcia(), "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isOk());
        pagar(aux, id);
        assertTrue(autorizacion(admin(), id).get("listoParaPreparar").asBoolean());

        // 1) La firma pasa a ser de la auxiliar (como en los pedidos antiguos)
        long idAuxiliar = jdbc.queryForObject("SELECT id FROM usuarios WHERE username = 'aux.sanroque'", Long.class);
        jdbc.update("UPDATE firmas SET usuario_id = ? WHERE pedido_id = ?", idAuxiliar, id);
        JsonNode a = autorizacion(admin(), id);
        assertEquals("PAGADO_PENDIENTE_FIRMA", a.get("clasificacion").asText());
        assertTrue(a.get("motivoFirma").asText().startsWith("Firmado por Sonia Ruiz"));
        patchJson(admin(), "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isBadRequest());

        // El veterinario asignado la sustituye por una firma válida
        patchJson(drGarcia(), "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isOk());
        assertTrue(autorizacion(admin(), id).get("listoParaPreparar").asBoolean());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM firmas WHERE pedido_id = ?", Integer.class, id));
        patchJson(drGarcia(), "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isBadRequest());

        // 2) El contenido cambia después de firmar
        jdbc.update("UPDATE lineas_pedido SET cantidad = cantidad + 1 WHERE pedido_id = ?", id);
        JsonNode b = autorizacion(admin(), id);
        assertEquals("La firma corresponde a un contenido anterior del pedido", b.get("motivoFirma").asText());
        patchJson(admin(), "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rechazo: solo el veterinario asignado; un pedido pagado se cancela con devolución pendiente")
    void rechazoDelVeterinario() throws Exception {
        MockHttpSession aux = auxSanRoque();
        int reservadoAntes = producto(9).get("stockReservado").asInt();
        long id = pedidoEnviadoAFirma(aux, 9);
        pagar(aux, id);
        String url = "/api/pedidos/" + id + "/rechazar";

        patchJson(aux, url, "{\"motivo\":\"No\"}").andExpect(status().isForbidden());
        patchJson(admin(), url, "{\"motivo\":\"No\"}").andExpect(status().isForbidden());
        patchJson(drMartin(), url, "{\"motivo\":\"No\"}").andExpect(status().isForbidden());
        patchJson(draLopez(), url, "{\"motivo\":\"No\"}").andExpect(status().isForbidden());
        patchJson(drGarcia(), url, "{\"motivo\":\"  \"}").andExpect(status().isBadRequest());

        patchJson(drGarcia(), url, "{\"motivo\":\"Cantidad no necesaria\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.devolucionPendiente").value(true));
        JsonNode pedido = json(getJson(admin(), "/api/pedidos/" + id));
        assertTrue(pedido.get("motivoCancelacion").asText().startsWith("Rechazado por Dr. Antonio García"));
        assertEquals("OTROS", pedido.get("autorizacion").get("clasificacion").asText());
        assertEquals(reservadoAntes, producto(9).get("stockReservado").asInt());

        // Uno ya firmado por él no se puede rechazar
        long firmado = pedidoEnviadoAFirma(aux, 9);
        patchJson(drGarcia(), "/api/pedidos/" + firmado + "/firmar", firmaDibujo()).andExpect(status().isOk());
        patchJson(drGarcia(), "/api/pedidos/" + firmado + "/rechazar", "{\"motivo\":\"x\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la lista de administración incluye la clasificación; un cliente solo ve sus pedidos")
    void permisosDeLaClasificacion() throws Exception {
        long id = pedidoEnviadoAFirma(auxSanRoque(), 5);
        JsonNode lista = json(getJson(admin(), "/api/pedidos").andExpect(status().isOk()));
        lista.forEach(p -> assertFalse(p.get("autorizacion").isNull(), "pedido sin clasificación"));
        getJson(auxMediterraneo(), "/api/pedidos/" + id).andExpect(status().isForbidden());
    }
}
