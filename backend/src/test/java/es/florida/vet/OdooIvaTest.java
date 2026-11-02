package es.florida.vet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import es.florida.vet.odoo.OdooConnector;
import es.florida.vet.odoo.OdooJsonRpcConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de contrato JSON-RPC para la fiscalidad propia de VetStock. */
class OdooIvaTest {

    private HttpServer server;
    private IvaOdooStub stub;
    private OdooConnector odoo;

    @BeforeEach
    void iniciarServidor() throws IOException {
        stub = new IvaOdooStub();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jsonrpc", stub::atender);
        server.start();
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("vetstock.odoo.url", "http://127.0.0.1:" + server.getAddress().getPort())
                .withProperty("vetstock.odoo.db", "pruebas")
                .withProperty("vetstock.odoo.usuario", "usuario-pruebas")
                .withProperty("vetstock.odoo.clave", "clave-pruebas");
        odoo = new OdooJsonRpcConnector(entorno);
    }

    @AfterEach
    void detenerServidor() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("el producto sustituye sólo impuestos de la compañía API por el IVA 4% no incluido")
    void productoSustituyeSoloImpuestosPropiosSinCambiarPrecio() {
        stub.impuestoVenta4Existe = false;
        OdooConnector.Resultado resultado = odoo.crearProducto(new OdooConnector.FichaProducto(
                "MED-IVA", "Producto neto", "Sin IVA incluido", new BigDecimal("14.50")));

        assertTrue(resultado.exito(), resultado.detalle());
        RpcCall busquedaGrupo = stub.primera("account.tax.group", "search");
        assertNotNull(busquedaGrupo);
        assertEquals("IVA 4% (VetStock)", busquedaGrupo.argumentosPosicionales().get(0).get(0).get(2).asText());
        assertEquals(1, busquedaGrupo.argumentosPosicionales().get(0).get(1).get(2).asInt());
        assertFalse(busquedaGrupo.argumentosNombrados().path("context").path("active_test").asBoolean(true));

        RpcCall creacionGrupo = stub.primera("account.tax.group", "create");
        assertNotNull(creacionGrupo);
        JsonNode grupo = creacionGrupo.argumentosPosicionales().get(0).get(0);
        assertEquals("IVA 4% (VetStock)", grupo.path("name").asText());
        assertEquals(1, grupo.path("company_id").asInt());
        assertEquals(233, grupo.path("country_id").asInt());
        RpcCall lecturaGrupo = stub.primera("account.tax.group", "read");
        assertNotNull(lecturaGrupo);
        assertTrue(solicitaCampo(lecturaGrupo, "name"));
        assertTrue(solicitaCampo(lecturaGrupo, "company_id"));
        assertTrue(solicitaCampo(lecturaGrupo, "country_id"));

        RpcCall creacionImpuesto = stub.primera("account.tax", "create");
        assertNotNull(creacionImpuesto);
        JsonNode impuesto = creacionImpuesto.argumentosPosicionales().get(0).get(0);
        assertEquals("VetStock IVA 4% (pruebas)", impuesto.path("name").asText());
        assertEquals(4, impuesto.path("amount").asInt());
        assertEquals("percent", impuesto.path("amount_type").asText());
        assertEquals("sale", impuesto.path("type_tax_use").asText());
        assertEquals(1, impuesto.path("company_id").asInt());
        assertEquals(233, impuesto.path("country_id").asInt());
        assertEquals("tax_excluded", impuesto.path("price_include_override").asText());
        assertFalse(impuesto.path("include_base_amount").asBoolean());
        assertEquals(55, impuesto.path("tax_group_id").asInt());
        RpcCall lecturaImpuesto = stub.primera("account.tax", "read");
        assertNotNull(lecturaImpuesto);
        assertTrue(solicitaCampo(lecturaImpuesto, "tax_group_id"));

        RpcCall escrituraProducto = stub.primera("product.template", "write");
        assertNotNull(escrituraProducto);
        JsonNode valores = escrituraProducto.argumentosPosicionales().get(1);
        assertFalse(valores.has("list_price"), "un precio ERP preexistente nunca se sobrescribe");
        assertEquals(6, valores.path("taxes_id").get(0).get(0).asInt());
        assertEquals(0, valores.path("taxes_id").get(0).get(1).asInt());
        JsonNode impuestos = valores.path("taxes_id").get(0).get(2);
        assertEquals(2, impuestos.size());
        assertEquals(90, impuestos.get(0).asInt(), "se conserva impuesto de otra compañía");
        assertEquals(44, impuestos.get(1).asInt(), "el IVA 15% propio se sustituye por el 4%");
    }

    @Test
    @DisplayName("un IVA 4% histórico sólo se reasigna al grupo VetStock sin alterar su importe ni el grupo anterior")
    void impuestoExistenteSeReubicaEnGrupoVetStockSinModificarDefinicionAjena() {
        stub.grupoVenta4Existe = true;

        OdooConnector.Resultado resultado = odoo.crearProducto(new OdooConnector.FichaProducto(
                "MED-IVA-LEGADO", "Producto legado", "Mantiene su precio", new BigDecimal("14.50")));

        assertTrue(resultado.exito(), resultado.detalle());
        assertFalse(stub.hay("account.tax", "create"), "el impuesto 4% existente no se duplica");
        RpcCall lecturaImpuesto = stub.primera("account.tax", "read");
        assertNotNull(lecturaImpuesto);
        assertTrue(solicitaCampo(lecturaImpuesto, "tax_group_id"));

        RpcCall escrituraImpuesto = stub.primera("account.tax", "write");
        assertNotNull(escrituraImpuesto);
        assertEquals(44, escrituraImpuesto.argumentosPosicionales().get(0).get(0).asInt());
        JsonNode valores = escrituraImpuesto.argumentosPosicionales().get(1);
        assertEquals(1, valores.size(), "la reparación sólo cambia la etiqueta fiscal");
        assertEquals(55, valores.path("tax_group_id").asInt());
        assertFalse(valores.has("amount"));
        assertFalse(valores.has("name"));
        assertFalse(valores.has("amount_type"));
        assertFalse(valores.has("type_tax_use"));
        assertEquals(new BigDecimal("4.00"), stub.importeImpuestoVenta4,
                "el importe del impuesto histórico se conserva");
        assertEquals("IVA 15%", stub.nombreGrupoLegado15,
                "el grupo fiscal antiguo no se renombra");
        assertFalse(stub.hay("account.tax.group", "write"), "no se modifica el grupo fiscal antiguo");
        assertFalse(stub.hay("sale.order", "write"), "la reparación fiscal no reescribe pedidos");
    }

    @Test
    @DisplayName("un pedido IVA 4 usa la lista VetStock EUR y no toca la moneda de la compañía")
    void pedidoNuevoUsaListaEurYNoAcumulaElIvaPorDefecto() {
        OdooConnector.Resultado resultado = odoo.crearPedido(fichaFiscal("VSM-EUR", null));

        assertTrue(resultado.exito(), resultado.detalle());
        assertEquals(99, resultado.odooId());
        RpcCall creacion = stub.primera("sale.order", "create");
        assertNotNull(creacion);
        JsonNode pedido = creacion.argumentosPosicionales().get(0).get(0);
        assertEquals(1, pedido.path("company_id").asInt());
        assertEquals(70, pedido.path("pricelist_id").asInt());
        assertFalse(pedido.has("currency_id"), "currency_id lo deriva Odoo de la lista de precios");
        JsonNode impuestosLinea = pedido.path("order_line").get(0).get(2).path("tax_id").get(0).get(2);
        assertEquals(1, impuestosLinea.size());
        assertEquals(44, impuestosLinea.get(0).asInt(), "la línea no debe acumular 4% y el IVA 15% por defecto");
        assertTrue(pedido.path("note").asText().contains("Desglose fiscal VetStock: neto 19.90 EUR; IVA 4.00% 0.80 EUR; total 20.70 EUR"));

        RpcCall activarEur = stub.primera("res.currency", "write");
        assertNotNull(activarEur, "EUR inactiva se debe activar");
        assertTrue(activarEur.argumentosPosicionales().get(1).path("active").asBoolean());
        assertFalse(stub.hay("res.company", "write"), "nunca debe cambiarse la moneda/configuración global de compañía");
    }

    @Test
    @DisplayName("un pedido vinculado sólo actualiza la nota y conserva una anotación manual")
    void pedidoExistenteSoloActualizaNotaYConservaManual() {
        stub.pedidoLeido = pedido("<p>Nota manual del equipo</p>", new BigDecimal("19.90"),
                new BigDecimal("0.80"), new BigDecimal("20.70"), 126, "EUR");

        OdooConnector.Resultado resultado = odoo.crearPedido(fichaFiscal("VSM-YA", 88));

        assertTrue(resultado.exito(), resultado.detalle());
        assertEquals(88, resultado.odooId());
        assertFalse(stub.hay("sale.order", "create"));
        RpcCall escritura = stub.primera("sale.order", "write");
        assertNotNull(escritura);
        JsonNode valores = escritura.argumentosPosicionales().get(1);
        assertEquals(1, valores.size(), "un pedido existente no recibe líneas, impuestos, moneda ni lista nuevos");
        assertTrue(valores.has("note"));
        String nota = valores.path("note").asText();
        assertTrue(nota.contains("Nota manual del equipo"));
        assertTrue(nota.contains("Estado de firma: Firmado"));
        assertTrue(nota.contains("Firmante: Dra. Vega (VETERINARIO)"));
        assertEquals(1, apariciones(nota, "VetStock Manager (VSM-YA)"));
    }

    @Test
    @DisplayName("una diferencia de importe o moneda después de crear no se declara como éxito")
    void pedidoConImporteOMonedaDistintosFallaSinReescribirlo() {
        stub.pedidoLeido = pedido("", new BigDecimal("19.89"), new BigDecimal("0.80"),
                new BigDecimal("20.69"), 2, "USD");

        OdooConnector.Resultado resultado = odoo.crearPedido(fichaFiscal("VSM-DIFIERE", null));

        assertFalse(resultado.exito());
        assertTrue(resultado.detalle().contains("Comprobación fiscal fallida"));
        assertTrue(resultado.detalle().contains("amount_untaxed"));
        assertTrue(resultado.detalle().contains("currency_id"));
        assertTrue(stub.hay("sale.order", "create"), "el fallo posterior se detecta leyendo el pedido creado");
        assertFalse(stub.hay("sale.order", "write"), "no se reescriben líneas/impuestos/pagos para forzar el ajuste");
    }

    @Test
    @DisplayName("la comprobación acepta IVA redondeado por línea a dos decimales")
    void pedidoConIvaRedondeadoPorLineaSeVerifica() {
        stub.pedidoLeido = pedido("", new BigDecimal("0.26"), new BigDecimal("0.02"),
                new BigDecimal("0.28"), 126, "EUR");
        OdooConnector.FichaPedido ficha = new OdooConnector.FichaPedido("VSM-REDONDEO", 7,
                List.of(new OdooConnector.LineaPedido("MED-001", 1, new BigDecimal("0.13")),
                        new OdooConnector.LineaPedido("MED-002", 1, new BigDecimal("0.13"))),
                null, "PENDIENTE", "Dra. Vega", null, null, null, "PAGADO",
                new BigDecimal("4.00"), new BigDecimal("0.26"), new BigDecimal("0.02"),
                new BigDecimal("0.28"), "EUR");

        OdooConnector.Resultado resultado = odoo.crearPedido(ficha);

        assertTrue(resultado.exito(), resultado.detalle());
        RpcCall creacion = stub.primera("sale.order", "create");
        assertEquals(2, creacion.argumentosPosicionales().get(0).get(0).path("order_line").size());
        assertEquals(44, creacion.argumentosPosicionales().get(0).get(0)
                .path("order_line").get(1).get(2).path("tax_id").get(0).get(2).get(0).asInt());
    }

    private static OdooConnector.FichaPedido fichaFiscal(String referencia, Integer idPedido) {
        return new OdooConnector.FichaPedido(referencia, 7,
                List.of(new OdooConnector.LineaPedido("MED-001", 2, new BigDecimal("9.95"))),
                idPedido, "FIRMADO", "Dra. Vega", "Dra. Vega", "VETERINARIO", "COL-42", "PAGADO",
                new BigDecimal("4.00"), new BigDecimal("19.90"), new BigDecimal("0.80"),
                new BigDecimal("20.70"), "EUR");
    }

    private static int apariciones(String texto, String aguja) {
        return texto.split(java.util.regex.Pattern.quote(aguja), -1).length - 1;
    }

    private static boolean solicitaCampo(RpcCall llamada, String campo) {
        return contieneTexto(llamada.argumentosPosicionales().path(1), campo)
                || contieneTexto(llamada.argumentosNombrados().path("fields"), campo);
    }

    private static boolean contieneTexto(JsonNode valores, String esperado) {
        for (JsonNode valor : valores) {
            if (esperado.equals(valor.asText())) {
                return true;
            }
        }
        return false;
    }

    private record RpcCall(String servicio, String metodo, JsonNode argumentos) {
        boolean es(String modelo, String operacion) {
            return "object".equals(servicio) && "execute_kw".equals(metodo)
                    && modelo.equals(argumentos.path(3).asText()) && operacion.equals(argumentos.path(4).asText());
        }

        JsonNode argumentosPosicionales() {
            return argumentos.path(5);
        }

        JsonNode argumentosNombrados() {
            return argumentos.path(6);
        }
    }

    /** Stub con los modelos y campos Odoo 18 que debe utilizar el adaptador. */
    private static final class IvaOdooStub {
        private static final ObjectMapper JSON = new ObjectMapper();
        private final List<RpcCall> llamadas = new CopyOnWriteArrayList<>();
        private boolean impuestoVenta4Existe = true;
        private boolean grupoVenta4Existe;
        private int grupoImpuestoVenta4Id = 15;
        private final BigDecimal importeImpuestoVenta4 = new BigDecimal("4.00");
        private final String nombreGrupoLegado15 = "IVA 15%";
        private JsonNode pedidoLeido = pedido("", new BigDecimal("19.90"), new BigDecimal("0.80"),
                new BigDecimal("20.70"), 126, "EUR");

        private void atender(HttpExchange intercambio) throws IOException {
            byte[] bytes = intercambio.getRequestBody().readAllBytes();
            JsonNode peticion = JSON.readTree(bytes);
            JsonNode params = peticion.path("params");
            RpcCall llamada = new RpcCall(params.path("service").asText(), params.path("method").asText(),
                    params.path("args").deepCopy());
            llamadas.add(llamada);
            ObjectNode respuesta = JSON.createObjectNode();
            respuesta.put("jsonrpc", "2.0");
            respuesta.set("result", resultado(llamada));
            byte[] salida = JSON.writeValueAsBytes(respuesta);
            intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            intercambio.sendResponseHeaders(200, salida.length);
            intercambio.getResponseBody().write(salida);
            intercambio.close();
        }

        private JsonNode resultado(RpcCall llamada) {
            if ("common".equals(llamada.servicio()) && "login".equals(llamada.metodo())) {
                return JSON.getNodeFactory().numberNode(2);
            }
            if (!"object".equals(llamada.servicio()) || !"execute_kw".equals(llamada.metodo())) {
                return JSON.getNodeFactory().booleanNode(true);
            }
            String modelo = llamada.argumentos().path(3).asText();
            String operacion = llamada.argumentos().path(4).asText();
            return switch (modelo) {
                case "res.users" -> usuario();
                case "res.company" -> compania();
                case "account.tax" -> impuestos(operacion, llamada);
                case "account.tax.group" -> gruposImpuestos(operacion, llamada);
                case "res.currency" -> moneda(operacion);
                case "product.pricelist" -> listaPrecios(operacion);
                case "product.template" -> producto(operacion);
                case "product.product" -> array(16);
                case "sale.order" -> pedidos(operacion);
                default -> JSON.getNodeFactory().booleanNode(true);
            };
        }

        private JsonNode usuario() {
            ObjectNode usuario = JSON.createObjectNode();
            usuario.put("id", 2);
            usuario.putArray("company_id").add(1).add("My Company");
            return array(usuario);
        }

        private JsonNode compania() {
            ObjectNode compania = JSON.createObjectNode();
            compania.put("id", 1);
            compania.putArray("currency_id").add(2).add("USD");
            compania.putArray("account_fiscal_country_id").add(233).add("United States");
            compania.put("tax_calculation_rounding_method", "round_per_line");
            return array(compania);
        }

        private JsonNode impuestos(String operacion, RpcCall llamada) {
            if ("search".equals(operacion)) {
                return impuestoVenta4Existe ? array(44) : array();
            }
            if ("create".equals(operacion)) {
                impuestoVenta4Existe = true;
                grupoImpuestoVenta4Id = llamada.argumentosPosicionales().get(0).get(0)
                        .path("tax_group_id").asInt();
                return array(44);
            }
            if ("write".equals(operacion)) {
                JsonNode valores = llamada.argumentosPosicionales().get(1);
                if (valores.has("tax_group_id")) {
                    grupoImpuestoVenta4Id = valores.path("tax_group_id").asInt();
                }
                return JSON.getNodeFactory().booleanNode(true);
            }
            if (!"read".equals(operacion)) {
                return JSON.getNodeFactory().booleanNode(true);
            }
            ArrayNode solicitados = (ArrayNode) llamada.argumentosPosicionales().get(0);
            ArrayNode respuesta = array();
            for (JsonNode valor : solicitados) {
                int id = valor.asInt();
                ObjectNode impuesto = JSON.createObjectNode();
                impuesto.put("id", id);
                if (id == 44) {
                    impuesto.put("name", "VetStock IVA 4% (pruebas)");
                    impuesto.putArray("company_id").add(1).add("My Company");
                    impuesto.putArray("country_id").add(233).add("United States");
                    impuesto.putArray("tax_group_id").add(grupoImpuestoVenta4Id)
                            .add(grupoImpuestoVenta4Id == 55 ? "IVA 4% (VetStock)" : nombreGrupoLegado15);
                    impuesto.put("amount", importeImpuestoVenta4);
                    impuesto.put("amount_type", "percent");
                    impuesto.put("type_tax_use", "sale");
                    impuesto.put("price_include", false);
                    impuesto.put("price_include_override", "tax_excluded");
                    impuesto.put("include_base_amount", false);
                    impuesto.put("active", true);
                } else if (id == 15) {
                    impuesto.putArray("company_id").add(1).add("My Company");
                } else {
                    impuesto.putArray("company_id").add(9).add("Otra compañía");
                }
                respuesta.add(impuesto);
            }
            return respuesta;
        }

        private JsonNode gruposImpuestos(String operacion, RpcCall llamada) {
            if ("search".equals(operacion)) {
                return grupoVenta4Existe ? array(55) : array();
            }
            if ("create".equals(operacion)) {
                grupoVenta4Existe = true;
                return array(55);
            }
            if (!"read".equals(operacion)) {
                return JSON.getNodeFactory().booleanNode(true);
            }
            ArrayNode solicitados = (ArrayNode) llamada.argumentosPosicionales().get(0);
            ArrayNode respuesta = array();
            for (JsonNode valor : solicitados) {
                int id = valor.asInt();
                ObjectNode grupo = JSON.createObjectNode();
                grupo.put("id", id);
                if (id == 55) {
                    grupo.put("name", "IVA 4% (VetStock)");
                    grupo.putArray("company_id").add(1).add("MyCompany");
                    grupo.putArray("country_id").add(233).add("US");
                } else if (id == 15) {
                    grupo.put("name", nombreGrupoLegado15);
                    grupo.putArray("company_id").add(1).add("MyCompany");
                    grupo.putArray("country_id").add(233).add("US");
                }
                respuesta.add(grupo);
            }
            return respuesta;
        }

        private JsonNode moneda(String operacion) {
            if ("search".equals(operacion)) {
                return array(126);
            }
            if ("read".equals(operacion)) {
                ObjectNode eur = JSON.createObjectNode();
                eur.put("id", 126);
                eur.put("name", "EUR");
                eur.put("active", false);
                return array(eur);
            }
            return JSON.getNodeFactory().booleanNode(true);
        }

        private JsonNode listaPrecios(String operacion) {
            if ("search".equals(operacion)) {
                return array();
            }
            if ("create".equals(operacion)) {
                return array(70);
            }
            if ("read".equals(operacion)) {
                ObjectNode lista = JSON.createObjectNode();
                lista.put("id", 70);
                lista.put("name", "VetStock EUR");
                lista.putArray("company_id").add(1).add("My Company");
                lista.putArray("currency_id").add(126).add("EUR");
                lista.put("active", true);
                return array(lista);
            }
            return JSON.getNodeFactory().booleanNode(true);
        }

        private JsonNode producto(String operacion) {
            if ("search".equals(operacion)) {
                return array(13);
            }
            if ("read".equals(operacion)) {
                ObjectNode producto = JSON.createObjectNode();
                producto.put("id", 13);
                producto.putArray("taxes_id").add(15).add(90);
                return array(producto);
            }
            return JSON.getNodeFactory().booleanNode(true);
        }

        private JsonNode pedidos(String operacion) {
            return switch (operacion) {
                case "search" -> array();
                case "create" -> array(99);
                case "read" -> pedidoLeido.deepCopy();
                case "write" -> JSON.getNodeFactory().booleanNode(true);
                default -> JSON.getNodeFactory().booleanNode(true);
            };
        }

        private boolean hay(String modelo, String operacion) {
            return llamadas.stream().anyMatch(llamada -> llamada.es(modelo, operacion));
        }

        private RpcCall primera(String modelo, String operacion) {
            return llamadas.stream().filter(llamada -> llamada.es(modelo, operacion)).findFirst().orElse(null);
        }

        private static ArrayNode array(int valor) {
            return JSON.createArrayNode().add(valor);
        }

        private static ArrayNode array(ObjectNode valor) {
            return JSON.createArrayNode().add(valor);
        }

        private static ArrayNode array() {
            return JSON.createArrayNode();
        }
    }

    private static ArrayNode pedido(String nota, BigDecimal base, BigDecimal iva, BigDecimal total,
                                    int monedaId, String nombreMoneda) {
        ObjectNode pedido = new ObjectMapper().createObjectNode();
        pedido.put("id", 99);
        pedido.put("note", nota);
        pedido.putArray("company_id").add(1).add("My Company");
        pedido.putArray("currency_id").add(monedaId).add(nombreMoneda);
        pedido.put("amount_untaxed", base);
        pedido.put("amount_tax", iva);
        pedido.put("amount_total", total);
        return new ObjectMapper().createArrayNode().add(pedido);
    }
}
