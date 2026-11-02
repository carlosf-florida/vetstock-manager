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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del adaptador JSON-RPC real sin requerir una instalación de Odoo.
 * El servidor local implementa únicamente las respuestas que necesita cada caso.
 */
class OdooJsonRpcConnectorTest {

    private HttpServer server;
    private OdooStub stub;
    private OdooConnector odoo;

    @BeforeEach
    void iniciarServidor() throws IOException {
        stub = new OdooStub();
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
    @DisplayName("un cliente con id de Odoo se lee y se actualiza siempre sobre ese mismo id")
    void actualizarClienteConIdLeeYEscribeElMismoContacto() {
        OdooConnector.Resultado resultado = odoo.crearCliente(new OdooConnector.FichaCliente(
                "Clínica Ágil", "Calle Ñandú 10", "600 111 222", "Dra. García", 7));

        assertTrue(resultado.exito());
        assertEquals(7, resultado.odooId());
        assertTrue(stub.hayLlamada("common", "login"));

        RpcCall lectura = stub.primeraEjecucion("res.partner", "read");
        assertNotNull(lectura);
        assertEquals(7, lectura.argumentosPosicionales().get(0).get(0).asInt());
        assertTrue(contieneTexto(lectura.argumentosPosicionales().path(1), "name")
                || contieneTexto(lectura.argumentosNombrados().path("fields"), "name"));
        assertTrue(contieneTexto(lectura.argumentosPosicionales().path(1), "comment")
                || contieneTexto(lectura.argumentosNombrados().path("fields"), "comment"));

        RpcCall escritura = stub.primeraEjecucion("res.partner", "write");
        assertNotNull(escritura);
        assertEquals(7, escritura.argumentosPosicionales().get(0).get(0).asInt());
        JsonNode valores = escritura.argumentosPosicionales().get(1);
        assertEquals("Clínica Ágil", valores.path("name").asText());
        assertEquals("Calle Ñandú 10", valores.path("street").asText());
        assertEquals("600 111 222", valores.path("phone").asText());
        assertFalse(stub.hayEjecucion("res.partner", "search"));
        assertFalse(stub.hayEjecucion("res.partner", "create"));
    }

    @Test
    @DisplayName("la actualización conserva la nota interna y envía acentos en UTF-8")
    void actualizarClienteConservaNotasYCaracteresUnicode() {
        OdooConnector.Resultado resultado = odoo.crearCliente(new OdooConnector.FichaCliente(
                "Clínica Ñandú", "Avenida del Río", "600 222 333", "Dra. María López", 7));

        assertTrue(resultado.exito());
        RpcCall escritura = stub.primeraEjecucion("res.partner", "write");
        assertNotNull(escritura);
        String comentario = escritura.argumentosPosicionales().get(1).path("comment").asText();
        String notaOriginal = "<p>Nota manual</p>";
        assertTrue(comentario.contains(notaOriginal), "la nota interna previa no debe perderse");
        assertTrue(comentario.contains("Dra. María López"));
        String textoAñadido = comentario.substring(comentario.indexOf(notaOriginal) + notaOriginal.length());
        assertTrue(textoAñadido.contains("<br") || textoAñadido.contains("<p>"),
                "el responsable debe añadirse como una línea HTML");
        assertTrue(escritura.cuerpoJson().contains("Clínica Ñandú"),
                "el cuerpo HTTP debe conservar los caracteres UTF-8");
    }

    @Test
    @DisplayName("si el id de cliente ya no existe, falla sin buscar ni crear un homónimo")
    void clienteConIdInexistenteNoBuscaNiCreaOtroContacto() {
        stub.respuestaLecturaCliente = OdooStub.ARRAY_VACIO.deepCopy();

        OdooConnector.Resultado resultado = odoo.crearCliente(new OdooConnector.FichaCliente(
                "Clínica desaparecida", "Calle Uno", "600 333 444", "Dra. Pérez", 7));

        assertFalse(resultado.exito());
        assertFalse(stub.hayEjecucion("res.partner", "search"));
        assertFalse(stub.hayEjecucion("res.partner", "create"));
        assertFalse(stub.hayEjecucion("res.partner", "write"));
    }

    @Test
    @DisplayName("sin id de Odoo se busca el cliente por nombre y se puede actualizar el encontrado")
    void clienteSinIdBuscaAntesDeActualizar() {
        stub.respuestaBusquedaCliente = OdooStub.arrayConEntero(7);

        OdooConnector.Resultado resultado = odoo.crearCliente(new OdooConnector.FichaCliente(
                "Clínica sin identificador", "Calle Dos", "600 444 555", "Dra. Ruiz"));

        assertTrue(resultado.exito());
        assertEquals(7, resultado.odooId());
        assertTrue(stub.hayEjecucion("res.partner", "search"));
        assertTrue(stub.hayEjecucion("res.partner", "write"));
        assertFalse(stub.hayEjecucion("res.partner", "create"));
    }

    @Test
    @DisplayName("un producto existente se localiza por default_code y se actualiza sin duplicarlo")
    void productoExistenteSeActualizaPorCodigo() {
        OdooConnector.Resultado resultado = odoo.crearProducto(new OdooConnector.FichaProducto(
                "MED-Ñ01", "Jeringa estéril", "Descripción con acento", new BigDecimal("14.50")));

        assertTrue(resultado.exito());
        assertEquals(13, resultado.odooId());
        RpcCall busqueda = stub.primeraEjecucion("product.template", "search");
        assertNotNull(busqueda);
        JsonNode criterio = busqueda.argumentosPosicionales().get(0).get(0);
        assertEquals("default_code", criterio.get(0).asText());
        assertEquals("MED-Ñ01", criterio.get(2).asText());

        RpcCall escritura = stub.primeraEjecucion("product.template", "write");
        assertNotNull(escritura);
        assertEquals(13, escritura.argumentosPosicionales().get(0).get(0).asInt());
        JsonNode valores = escritura.argumentosPosicionales().get(1);
        assertEquals("Jeringa estéril", valores.path("name").asText());
        assertEquals("Descripción con acento", valores.path("description").asText());
        assertFalse(stub.hayEjecucion("product.template", "create"));
    }

    @Test
    @DisplayName("un pedido nuevo conserva referencia y origen de VetStock en Odoo")
    void pedidoNuevoIncluyeReferenciaYOrigen() {
        OdooConnector.Resultado resultado = odoo.crearPedido(new OdooConnector.FichaPedido(
                "VSM-42", 7, List.of(new OdooConnector.LineaPedido("MED-001", 2, new BigDecimal("9.95")))));

        assertTrue(resultado.exito());
        assertEquals(99, resultado.odooId());
        RpcCall creacion = stub.primeraEjecucion("sale.order", "create");
        assertNotNull(creacion);
        JsonNode valores = creacion.argumentosPosicionales().get(0).get(0);
        assertEquals("VSM-42", valores.path("client_order_ref").asText());
        assertEquals("VetStock Manager", valores.path("origin").asText());
    }

    private static boolean contieneTexto(JsonNode valores, String esperado) {
        for (JsonNode valor : valores) {
            if (esperado.equals(valor.asText())) {
                return true;
            }
        }
        return false;
    }

    private record RpcCall(String servicio, String metodo, JsonNode argumentos, String cuerpoJson) {
        boolean esEjecucion(String modelo, String operacion) {
            return "object".equals(servicio)
                    && "execute_kw".equals(metodo)
                    && modelo.equals(argumentos.path(3).asText())
                    && operacion.equals(argumentos.path(4).asText());
        }

        JsonNode argumentosPosicionales() {
            return argumentos.path(5);
        }

        JsonNode argumentosNombrados() {
            return argumentos.path(6);
        }
    }

    /** Servidor JSON-RPC mínimo que registra todas las peticiones para las aserciones. */
    private static final class OdooStub {
        private static final ObjectMapper JSON = new ObjectMapper();
        private static final ArrayNode ARRAY_VACIO = JSON.createArrayNode();

        private final List<RpcCall> llamadas = new CopyOnWriteArrayList<>();
        private JsonNode respuestaLecturaCliente = clienteExistente();
        private JsonNode respuestaBusquedaCliente = ARRAY_VACIO.deepCopy();
        private boolean impuestoVenta4Existe = true;
        private boolean grupoVenta4Existe = true;
        private int grupoImpuestoVenta4Id = 15;
        private final BigDecimal importeImpuestoVenta4 = new BigDecimal("4.00");
        private final String nombreGrupoLegado15 = "IVA 15%";

        private void atender(HttpExchange intercambio) throws IOException {
            byte[] bytes = intercambio.getRequestBody().readAllBytes();
            String cuerpo = new String(bytes, StandardCharsets.UTF_8);
            JsonNode peticion = JSON.readTree(bytes);
            JsonNode params = peticion.path("params");
            String servicio = params.path("service").asText();
            String metodo = params.path("method").asText();
            JsonNode argumentos = params.path("args").deepCopy();
            RpcCall llamada = new RpcCall(servicio, metodo, argumentos, cuerpo);
            llamadas.add(llamada);

            ObjectNode respuesta = JSON.createObjectNode();
            respuesta.put("jsonrpc", "2.0");
            respuesta.set("result", resultadoPara(llamada));
            byte[] salida = JSON.writeValueAsBytes(respuesta);
            intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            intercambio.sendResponseHeaders(200, salida.length);
            intercambio.getResponseBody().write(salida);
            intercambio.close();
        }

        private JsonNode resultadoPara(RpcCall llamada) {
            if ("common".equals(llamada.servicio()) && "login".equals(llamada.metodo())) {
                return JSON.getNodeFactory().numberNode(42);
            }
            if (!"object".equals(llamada.servicio()) || !"execute_kw".equals(llamada.metodo())) {
                return JSON.getNodeFactory().booleanNode(true);
            }

            String modelo = llamada.argumentos().path(3).asText();
            String operacion = llamada.argumentos().path(4).asText();
            return switch (modelo) {
                case "res.users" -> "read".equals(operacion) ? usuarioApi() : JSON.getNodeFactory().booleanNode(true);
                case "res.company" -> "read".equals(operacion) ? companiaApi() : JSON.getNodeFactory().booleanNode(true);
                case "res.currency" -> switch (operacion) {
                    case "search" -> arrayConEntero(126);
                    case "read" -> monedaEurInactiva();
                    case "write" -> JSON.getNodeFactory().booleanNode(true);
                    default -> JSON.getNodeFactory().booleanNode(true);
                };
                case "account.tax" -> impuestos(operacion, llamada);
                case "account.tax.group" -> gruposImpuestos(operacion, llamada);
                case "product.pricelist" -> switch (operacion) {
                    case "search" -> ARRAY_VACIO.deepCopy();
                    case "create" -> arrayConEntero(70);
                    case "read" -> listaPrecioEur();
                    default -> JSON.getNodeFactory().booleanNode(true);
                };
                case "res.partner" -> switch (operacion) {
                    case "read" -> respuestaLecturaCliente.deepCopy();
                    case "search" -> respuestaBusquedaCliente.deepCopy();
                    case "write" -> JSON.getNodeFactory().booleanNode(true);
                    case "create" -> arrayConEntero(71);
                    default -> JSON.getNodeFactory().booleanNode(true);
                };
                case "product.template" -> switch (operacion) {
                    case "search" -> arrayConEntero(13);
                    case "read" -> productoExistente();
                    case "write" -> JSON.getNodeFactory().booleanNode(true);
                    case "create" -> arrayConEntero(13);
                    default -> JSON.getNodeFactory().booleanNode(true);
                };
                case "product.product" -> "search".equals(operacion)
                        ? arrayConEntero(16) : JSON.getNodeFactory().booleanNode(true);
                case "sale.order" -> switch (operacion) {
                    case "search" -> ARRAY_VACIO.deepCopy();
                    case "create" -> arrayConEntero(99);
                    default -> JSON.getNodeFactory().booleanNode(true);
                };
                default -> JSON.getNodeFactory().booleanNode(true);
            };
        }

        private boolean hayLlamada(String servicio, String metodo) {
            return llamadas.stream().anyMatch(llamada -> servicio.equals(llamada.servicio()) && metodo.equals(llamada.metodo()));
        }

        private boolean hayEjecucion(String modelo, String operacion) {
            return llamadas.stream().anyMatch(llamada -> llamada.esEjecucion(modelo, operacion));
        }

        private RpcCall primeraEjecucion(String modelo, String operacion) {
            return llamadas.stream().filter(llamada -> llamada.esEjecucion(modelo, operacion)).findFirst().orElse(null);
        }

        private static ArrayNode arrayConEntero(int valor) {
            return JSON.createArrayNode().add(valor);
        }

        private static ArrayNode clienteExistente() {
            ObjectNode cliente = JSON.createObjectNode();
            cliente.put("id", 7);
            cliente.put("name", "Nombre anterior");
            cliente.put("comment", "<p>Nota manual</p>");
            return JSON.createArrayNode().add(cliente);
        }

        private static ArrayNode usuarioApi() {
            ObjectNode usuario = JSON.createObjectNode();
            usuario.put("id", 42);
            usuario.putArray("company_id").add(1).add("My Company");
            return JSON.createArrayNode().add(usuario);
        }

        private static ArrayNode companiaApi() {
            ObjectNode compania = JSON.createObjectNode();
            compania.put("id", 1);
            compania.putArray("currency_id").add(2).add("USD");
            compania.putArray("account_fiscal_country_id").add(233).add("United States");
            compania.put("tax_calculation_rounding_method", "round_per_line");
            return JSON.createArrayNode().add(compania);
        }

        private static ArrayNode monedaEurInactiva() {
            ObjectNode moneda = JSON.createObjectNode();
            moneda.put("id", 126);
            moneda.put("name", "EUR");
            moneda.put("active", false);
            return JSON.createArrayNode().add(moneda);
        }

        private static ArrayNode listaPrecioEur() {
            ObjectNode lista = JSON.createObjectNode();
            lista.put("id", 70);
            lista.put("name", "VetStock EUR");
            lista.putArray("company_id").add(1).add("My Company");
            lista.putArray("currency_id").add(126).add("EUR");
            lista.put("active", true);
            return JSON.createArrayNode().add(lista);
        }

        private static ArrayNode productoExistente() {
            ObjectNode producto = JSON.createObjectNode();
            producto.put("id", 13);
            producto.putArray("taxes_id").add(15).add(90);
            return JSON.createArrayNode().add(producto);
        }

        private JsonNode impuestos(String operacion, RpcCall llamada) {
            return switch (operacion) {
                case "search" -> impuestoVenta4Existe ? arrayConEntero(44) : ARRAY_VACIO.deepCopy();
                case "create" -> {
                    impuestoVenta4Existe = true;
                    grupoImpuestoVenta4Id = llamada.argumentosPosicionales().get(0).get(0)
                            .path("tax_group_id").asInt();
                    yield arrayConEntero(44);
                }
                case "read" -> impuestosSolicitados(llamada);
                case "write" -> {
                    JsonNode valores = llamada.argumentosPosicionales().get(1);
                    if (valores.has("tax_group_id")) {
                        grupoImpuestoVenta4Id = valores.path("tax_group_id").asInt();
                    }
                    yield JSON.getNodeFactory().booleanNode(true);
                }
                default -> JSON.getNodeFactory().booleanNode(true);
            };
        }

        private JsonNode gruposImpuestos(String operacion, RpcCall llamada) {
            return switch (operacion) {
                case "search" -> grupoVenta4Existe ? arrayConEntero(55) : ARRAY_VACIO.deepCopy();
                case "create" -> {
                    grupoVenta4Existe = true;
                    yield arrayConEntero(55);
                }
                case "read" -> gruposSolicitados(llamada);
                default -> JSON.getNodeFactory().booleanNode(true);
            };
        }

        private ArrayNode impuestosSolicitados(RpcCall llamada) {
            ArrayNode solicitados = (ArrayNode) llamada.argumentosPosicionales().get(0);
            ArrayNode resultado = JSON.createArrayNode();
            for (JsonNode solicitado : solicitados) {
                int id = solicitado.asInt();
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
                    impuesto.putArray("company_id").add(9).add("Otra empresa");
                }
                resultado.add(impuesto);
            }
            return resultado;
        }

        private ArrayNode gruposSolicitados(RpcCall llamada) {
            ArrayNode solicitados = (ArrayNode) llamada.argumentosPosicionales().get(0);
            ArrayNode resultado = JSON.createArrayNode();
            for (JsonNode solicitado : solicitados) {
                int id = solicitado.asInt();
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
                resultado.add(grupo);
            }
            return resultado;
        }
    }
}
