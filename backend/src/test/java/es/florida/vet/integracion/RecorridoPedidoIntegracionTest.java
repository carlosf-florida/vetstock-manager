package es.florida.vet.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import es.florida.vet.service.ReservaCaducidadTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorrido principal de la aplicación de principio a fin, contra la API real
 * y MySQL: creación, firma, pago (fallido, reintento, confirmado), preparación,
 * bultos, palet, destino y expedición, con sus reglas de stock.
 */
class RecorridoPedidoIntegracionTest extends IntegracionBase {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ReservaCaducidadTask caducidad;

    @Test
    @DisplayName("recorrido completo: auxiliar crea, veterinario firma, la clínica paga y administración expide")
    void recorridoCompleto() throws Exception {
        MockHttpSession aux = auxSanRoque();
        MockHttpSession garcia = drGarcia();
        MockHttpSession admin = admin();
        JsonNode antes = producto(1);

        // 1. El auxiliar crea el pedido: el firmante asignado es el responsable de su clínica
        long id = crearPedido(aux, 1, 5);
        JsonNode pedido = json(getJson(aux, "/api/pedidos/" + id).andExpect(status().isOk()));
        assertEquals("BORRADOR", pedido.get("estado").asText());
        assertEquals("dr.garcia", pedido.get("veterinarioUsername").asText());
        assertEquals("Sonia Ruiz (Auxiliar)", pedido.get("creadoPorNombre").asText());

        // 2. Lo deja pendiente de firma: se reserva el stock
        patchJson(aux, "/api/pedidos/" + id + "/enviar-firma", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE_FIRMA"));
        assertEquals(antes.get("stockReservado").asInt() + 5, producto(1).get("stockReservado").asInt());

        // 3. Ni el auxiliar ni otro veterinario de la clínica pueden firmar; nadie puede preparar aún
        //    (pagar antes de firmar está permitido: se prueba en ClasificacionFirmaPagoIntegracionTest)
        patchJson(aux, "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isForbidden());
        patchJson(draLopez(), "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isForbidden());
        patchJson(admin, "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isBadRequest());

        // 4. El veterinario asignado revisa y firma: queda FIRMADO (pendiente de pago) y bloqueado
        patchJson(garcia, "/api/pedidos/" + id + "/firmar", firmaDibujo())
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FIRMADO"));
        putJson(aux, "/api/pedidos/" + id + "/lineas", "{\"lineas\":[{\"productoId\":1,\"cantidad\":50}]}")
                .andExpect(status().isBadRequest());
        patchJson(garcia, "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isBadRequest());
        JsonNode firma = json(getJson(aux, "/api/pedidos/" + id + "/firma").andExpect(status().isOk()));
        assertEquals("dr.garcia", firma.get("firmanteUsername").asText());
        assertTrue(firma.get("contenidoPedido").asText().contains("\"cantidad\":5"));
        patchJson(admin, "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isBadRequest());

        // 5. Primer pago FALLADO: el pedido sigue pendiente de pago
        String ref1 = json(postJson(aux, "/api/pagos/" + id + "/iniciar", "{}").andExpect(status().isCreated()))
                .get("referencia").asText();
        postJson(aux, "/api/pagos/simulado/" + ref1, "{\"resultado\":\"FALLADO\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FALLADO"));
        getJson(aux, "/api/pedidos/" + id).andExpect(jsonPath("$.estado").value("FIRMADO"));

        // 6. Reintento: referencia nueva; pedirlo otra vez devuelve el MISMO intento (sin cobro doble)
        JsonNode reintento = json(postJson(aux, "/api/pagos/" + id + "/iniciar", "{}").andExpect(status().isCreated()));
        String ref2 = reintento.get("referencia").asText();
        assertNotEquals(ref1, ref2);
        assertEquals(ref2, json(postJson(aux, "/api/pagos/" + id + "/iniciar", "{}")).get("referencia").asText());
        String total = reintento.get("importe").asText();

        // 7. Notificaciones no válidas: firma HMAC falsa o importe/moneda distintos
        String cuerpo = "{\"referencia\":\"" + ref2 + "\",\"importe\":" + total + ",\"moneda\":\"EUR\",\"resultado\":\"CONFIRMADO\"}";
        notificarPagoConFirma(cuerpo, "firma-falsa").andExpect(status().isForbidden());
        notificarPagoConFirma(cuerpo, null).andExpect(status().isForbidden());
        notificarPago(ref2, "0.01", "EUR", "CONFIRMADO").andExpect(status().isBadRequest());
        getJson(aux, "/api/pedidos/" + id).andExpect(jsonPath("$.estado").value("FIRMADO"));

        // 8. El intento manipulado quedó FALLADO; el tercer intento se confirma por el webhook
        String ref3 = json(postJson(aux, "/api/pagos/" + id + "/iniciar", "{}").andExpect(status().isCreated()))
                .get("referencia").asText();
        assertNotEquals(ref2, ref3);
        notificarPago(ref3, "1.00", "USD", "CONFIRMADO").andExpect(status().isBadRequest());
        String ref4 = json(postJson(aux, "/api/pagos/" + id + "/iniciar", "{}")).get("referencia").asText();
        notificarPago(ref4, total, "EUR", "CONFIRMADO")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADO"));
        JsonNode pagado = json(getJson(aux, "/api/pedidos/" + id));
        assertEquals("LISTO_PREPARAR", pagado.get("estado").asText());
        String fechaPago = pagado.get("fechaPago").asText();

        // 9. Notificación duplicada: no cambia nada
        notificarPago(ref4, total, "EUR", "CONFIRMADO").andExpect(status().isOk());
        assertEquals(fechaPago, json(getJson(aux, "/api/pedidos/" + id)).get("fechaPago").asText());
        postJson(aux, "/api/pagos/" + id + "/iniciar", "{}").andExpect(status().isBadRequest());
        patchJson(aux, "/api/pedidos/" + id + "/cancelar", null).andExpect(status().isBadRequest());

        // 10. Preparación: sin bultos no se marca preparado; sin palet no se expide
        patchJson(aux, "/api/pedidos/" + id + "/iniciar-preparacion", null).andExpect(status().isForbidden());
        patchJson(admin, "/api/pedidos/" + id + "/iniciar-preparacion", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
        patchJson(admin, "/api/pedidos/" + id + "/preparar", null).andExpect(status().isBadRequest());
        long bulto = json(postJson(admin, "/api/bultos", "{\"identificador\":\"IT-B-" + id + "\",\"pedidoId\":" + id + "}")
                .andExpect(status().isCreated())).get("id").asLong();
        // Al prepararse toma la salida habitual de su clínica (San Roque → 14 - Regional)
        patchJson(admin, "/api/pedidos/" + id + "/preparar", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PREPARADO"))
                .andExpect(jsonPath("$.salida").value("14 - Regional")).andExpect(jsonPath("$.bultos").value(1));
        patchJson(admin, "/api/pedidos/" + id + "/expedir", null).andExpect(status().isBadRequest());

        // Un palet de otra salida (Valencia) no admite el bulto; el de Regional sí
        long paletValencia = json(postJson(admin, "/api/palets", "{\"identificador\":\"IT-PV-" + id + "\",\"destinoId\":1}")
                .andExpect(status().isCreated())).get("id").asLong();
        patchJson(admin, "/api/bultos/" + bulto + "/asignar-palet", "{\"paletId\":" + paletValencia + "}").andExpect(status().isBadRequest());
        long palet = json(postJson(admin, "/api/palets", "{\"identificador\":\"IT-P-" + id + "\",\"destinoId\":3}")
                .andExpect(status().isCreated())).get("id").asLong();
        patchJson(admin, "/api/bultos/" + bulto + "/asignar-palet", "{\"paletId\":" + palet + "}").andExpect(status().isOk());
        JsonNode contenidoPalet = json(getJson(admin, "/api/palets/" + palet).andExpect(status().isOk()));
        assertEquals("Regional", contenidoPalet.get("destinoNombre").asText());
        assertEquals(id, contenidoPalet.get("bultos").get(0).get("pedidoId").asLong());

        // 11. Expedición: descuenta stock físico y libera la reserva UNA sola vez
        patchJson(admin, "/api/pedidos/" + id + "/expedir", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EXPEDIDO"));
        JsonNode despues = producto(1);
        assertEquals(antes.get("stockActual").asInt() - 5, despues.get("stockActual").asInt());
        assertEquals(antes.get("stockReservado").asInt(), despues.get("stockReservado").asInt());
        patchJson(admin, "/api/pedidos/" + id + "/expedir", null).andExpect(status().isBadRequest());
        assertEquals(despues.get("stockActual").asInt(), producto(1).get("stockActual").asInt());
    }

    @Test
    @DisplayName("administración crea y modifica un pedido en nombre de una clínica y queda registrada la autoría")
    void administracionAyudaAlCliente() throws Exception {
        MockHttpSession admin = admin();
        String cuerpo = "{\"clinicaId\":1,\"veterinarioId\":1,\"lineas\":[{\"productoId\":5,\"cantidad\":2}]}";
        long id = json(postJson(admin, "/api/pedidos", cuerpo).andExpect(status().isCreated())).get("id").asLong();
        putJson(admin, "/api/pedidos/" + id + "/lineas", "{\"lineas\":[{\"productoId\":5,\"cantidad\":3}]}")
                .andExpect(status().isOk());

        JsonNode pedido = json(getJson(auxSanRoque(), "/api/pedidos/" + id).andExpect(status().isOk()));
        assertEquals("Laura Sánchez (Almacén)", pedido.get("creadoPorNombre").asText());
        assertEquals("Laura Sánchez (Almacén)", pedido.get("modificadoPorNombre").asText());
        assertEquals("dr.garcia", pedido.get("veterinarioUsername").asText());

        // Administración no puede firmar por el veterinario ni pagar por la clínica
        patchJson(admin, "/api/pedidos/" + id + "/enviar-firma", null).andExpect(status().isOk());
        patchJson(admin, "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isForbidden());
        patchJson(drGarcia(), "/api/pedidos/" + id + "/firmar", firmaDibujo()).andExpect(status().isOk());
        postJson(admin, "/api/pagos/" + id + "/iniciar", "{}").andExpect(status().isForbidden());

        // Validaciones de entrada: cantidades enteras positivas y observaciones de 255 caracteres como máximo
        postJson(admin, "/api/pedidos", "{\"clinicaId\":1,\"veterinarioId\":1,\"lineas\":[{\"productoId\":5,\"cantidad\":0}]}")
                .andExpect(status().isBadRequest());
        postJson(admin, "/api/pedidos", "{\"clinicaId\":1,\"veterinarioId\":1,\"observaciones\":\"" + "x".repeat(256)
                + "\",\"lineas\":[{\"productoId\":5,\"cantidad\":1}]}").andExpect(status().isBadRequest());
        // Un veterinario de otra clínica no puede ser el firmante
        postJson(admin, "/api/pedidos", "{\"clinicaId\":1,\"veterinarioId\":3,\"lineas\":[{\"productoId\":5,\"cantidad\":1}]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el veterinario responsable firma su propio pedido")
    void veterinarioFirmaSuPropioPedido() throws Exception {
        MockHttpSession martin = drMartin();
        long id = crearPedido(martin, 2, 1);
        patchJson(martin, "/api/pedidos/" + id + "/enviar-firma", null).andExpect(status().isOk());
        patchJson(martin, "/api/pedidos/" + id + "/firmar", firmaDibujo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FIRMADO"))
                .andExpect(jsonPath("$.firmanteRol").value("VETERINARIO"));
    }

    @Test
    @DisplayName("validación de la firma: formato, tamaño, confirmación y nombre del archivo")
    void validacionArchivoFirma() throws Exception {
        MockHttpSession martin = drMartin();
        long id = crearPedido(martin, 2, 1);
        patchJson(martin, "/api/pedidos/" + id + "/enviar-firma", null).andExpect(status().isOk());
        String url = "/api/pedidos/" + id + "/firmar";

        String gif = Base64.getEncoder().encodeToString("GIF89a-no-es-png".getBytes());
        patchJson(martin, url, "{\"tipoFirma\":\"IMAGEN\",\"imagenBase64\":\"" + gif + "\",\"nombreArchivo\":\"f.gif\",\"confirmacion\":true}")
                .andExpect(status().isBadRequest());

        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        grande[0] = (byte) 0x89; grande[1] = 'P'; grande[2] = 'N'; grande[3] = 'G';
        patchJson(martin, url, "{\"tipoFirma\":\"IMAGEN\",\"imagenBase64\":\"" + Base64.getEncoder().encodeToString(grande)
                + "\",\"nombreArchivo\":\"f.png\",\"confirmacion\":true}").andExpect(status().isBadRequest());

        patchJson(martin, url, "{\"tipoFirma\":\"DIBUJO\",\"imagenBase64\":\"" + PNG_1X1 + "\",\"confirmacion\":false}")
                .andExpect(status().isBadRequest());
        patchJson(martin, url, "{\"tipoFirma\":\"IMAGEN\",\"imagenBase64\":\"" + PNG_1X1 + "\",\"confirmacion\":true}")
                .andExpect(status().isBadRequest());
        getJson(martin, "/api/pedidos/" + id).andExpect(jsonPath("$.estado").value("PENDIENTE_FIRMA"));

        String jpg = Base64.getEncoder().encodeToString(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10});
        patchJson(martin, url, "{\"tipoFirma\":\"IMAGEN\",\"imagenBase64\":\"" + jpg + "\",\"nombreArchivo\":\"firma.jpg\",\"confirmacion\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FIRMADO"));
    }

    @Test
    @DisplayName("stock insuficiente y dos reservas simultáneas de las últimas unidades: solo una lo consigue")
    void stockInsuficienteYReservasConcurrentes() throws Exception {
        // Producto 12 (Dieta renal): 3 unidades en stock y ninguna reservada en los datos iniciales
        MockHttpSession aux1 = auxSanRoque();
        MockHttpSession aux2 = auxMediterraneo();
        long excesivo = crearPedido(aux1, 12, 50);
        patchJson(aux1, "/api/pedidos/" + excesivo + "/enviar-firma", null).andExpect(status().isBadRequest());

        long pedido1 = crearPedido(aux1, 12, 2);
        long pedido2 = crearPedido(aux2, 12, 2);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (Object[] caso : new Object[][]{{aux1, pedido1}, {aux2, pedido2}}) {
            resultados.add(hilos.submit(() -> {
                salida.await();
                return patchJson((MockHttpSession) caso[0], "/api/pedidos/" + caso[1] + "/enviar-firma", null)
                        .andReturn().getResponse().getStatus();
            }));
        }
        salida.countDown();
        List<Integer> codigos = new ArrayList<>();
        for (Future<Integer> r : resultados) codigos.add(r.get());
        hilos.shutdown();

        assertEquals(1, codigos.stream().filter(c -> c == 200).count(), "códigos: " + codigos);
        assertEquals(1, codigos.stream().filter(c -> c == 400).count(), "códigos: " + codigos);
        JsonNode dieta = producto(12);
        assertEquals(2, dieta.get("stockReservado").asInt());
        assertTrue(dieta.get("stockDisponible").asInt() >= 0);
    }

    @Test
    @DisplayName("caducidad: un pedido sin pago tras 72 h se cancela y libera la reserva")
    void caducidadDeReservas() throws Exception {
        MockHttpSession aux = auxSanRoque();
        int reservadoInicial = producto(10).get("stockReservado").asInt();
        long id = crearPedido(aux, 10, 4);
        patchJson(aux, "/api/pedidos/" + id + "/enviar-firma", null).andExpect(status().isOk());
        assertEquals(reservadoInicial + 4, producto(10).get("stockReservado").asInt());

        jdbc.update("UPDATE pedidos SET fecha_envio_firma = NOW() - INTERVAL 73 HOUR WHERE id = ?", id);
        caducidad.revisar();

        JsonNode pedido = json(getJson(aux, "/api/pedidos/" + id));
        assertEquals("CANCELADO", pedido.get("estado").asText());
        assertTrue(pedido.get("motivoCancelacion").asText().startsWith("Reserva caducada"));
        assertEquals(reservadoInicial, producto(10).get("stockReservado").asInt());
    }
}
