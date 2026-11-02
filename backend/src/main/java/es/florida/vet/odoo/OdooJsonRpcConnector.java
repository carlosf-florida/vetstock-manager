package es.florida.vet.odoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Adaptador real de Odoo 18 mediante JSON-RPC.
 *
 * <p>Los contactos se actualizan por su identificador Odoo y los productos se
 * localizan por {@code default_code}. La configuración fiscal creada por este
 * adaptador está limitada a la compañía activa del usuario API; no toca la
 * fiscalidad global de Odoo ni la moneda de la compañía.</p>
 */
@Component
@ConditionalOnProperty(name = "vetstock.odoo.modo", havingValue = "REAL")
public class OdooJsonRpcConnector implements OdooConnector {

    private static final String NOMBRE_IMPUESTO_IVA4 = "VetStock IVA 4% (pruebas)";
    private static final String NOMBRE_GRUPO_IVA4 = "IVA 4% (VetStock)";
    private static final String NOMBRE_LISTA_EUR = "VetStock EUR";
    private static final String CODIGO_EUR = "EUR";
    private static final BigDecimal PORCENTAJE_IVA4 = new BigDecimal("4.00");

    private final String url;
    private final String db;
    private final String usuario;
    private final String clave;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final Object cerrojo = new Object();

    private Integer uid;
    private ConfiguracionEmpresa empresaActual;
    private Integer impuestoVenta4Id;
    private Integer grupoVenta4Id;
    private Integer monedaEurId;
    private Integer listaPrecioEurId;

    public OdooJsonRpcConnector(org.springframework.core.env.Environment env) {
        this.url = env.getProperty("vetstock.odoo.url", "http://localhost:8069");
        this.db = env.getProperty("vetstock.odoo.db", "");
        this.usuario = env.getProperty("vetstock.odoo.usuario", "");
        this.clave = env.getProperty("vetstock.odoo.clave", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private JsonNode llamar(String servicio, String metodo, JsonNode args) {
        try {
            ObjectNode cuerpo = json.createObjectNode();
            cuerpo.put("jsonrpc", "2.0");
            cuerpo.put("method", "call");
            ObjectNode params = cuerpo.putObject("params");
            params.put("service", servicio);
            params.put("method", metodo);
            params.set("args", args);
            HttpRequest peticion = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/jsonrpc"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> respuesta = http.send(peticion,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
                throw new IllegalStateException("Odoo respondió HTTP " + respuesta.statusCode());
            }
            JsonNode nodo = json.readTree(respuesta.body());
            if (nodo.has("error")) {
                throw new IllegalStateException("Odoo devolvió un error: "
                        + nodo.path("error").path("data").path("message").asText("Error JSON-RPC"));
            }
            if (!nodo.has("result")) {
                throw new IllegalStateException("Respuesta JSON-RPC sin resultado");
            }
            return nodo.get("result");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Se interrumpió la conexión con Odoo", e);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo hablar con Odoo: " + e.getMessage(), e);
        }
    }

    private Integer uid() {
        synchronized (cerrojo) {
            if (uid == null) {
                ArrayNode args = array().add(db).add(usuario).add(clave);
                JsonNode resultado = llamar("common", "login", args);
                if (resultado == null || !resultado.isIntegralNumber() || resultado.asInt() <= 0) {
                    throw new IllegalStateException("Las credenciales de Odoo no son válidas");
                }
                uid = resultado.asInt();
            }
            return uid;
        }
    }

    private JsonNode ejecutar(String modelo, String metodo, ArrayNode posicionales, ObjectNode kwargs) {
        ArrayNode args = array().add(db).add(uid()).add(clave).add(modelo).add(metodo);
        args.add(posicionales);
        args.add(kwargs == null ? json.createObjectNode() : kwargs);
        return llamar("object", "execute_kw", args);
    }

    private ArrayNode array() {
        return json.createArrayNode();
    }

    /** Incluye archivados para detectar configuraciones duplicadas o alteradas. */
    private JsonNode buscar(String modelo, ArrayNode dominio) {
        ObjectNode kwargs = json.createObjectNode();
        kwargs.putObject("context").put("active_test", false);
        return ejecutar(modelo, "search", array().add(dominio), kwargs);
    }

    private JsonNode leerRegistro(String modelo, int id, String... campos) {
        ObjectNode kwargs = json.createObjectNode();
        ArrayNode fields = kwargs.putArray("fields");
        for (String campo : campos) {
            fields.add(campo);
        }
        JsonNode registros = ejecutar(modelo, "read", array().add(array().add(id)), kwargs);
        if (!registros.isArray() || registros.size() != 1) {
            throw new IllegalStateException("No se encontró " + modelo + " con id " + id + " en Odoo");
        }
        return registros.get(0);
    }

    private void escribir(String modelo, int id, ObjectNode valores) {
        ArrayNode args = array().add(array().add(id)).add(valores);
        if (!ejecutar(modelo, "write", args, null).asBoolean(false)) {
            throw new IllegalStateException("Odoo no confirmó la actualización de " + modelo + " (id " + id + ")");
        }
    }

    private int crear(String modelo, ObjectNode valores) {
        JsonNode ids = ejecutar(modelo, "create", array().add(array().add(valores)), null);
        if (!ids.isArray() || ids.isEmpty() || !ids.get(0).isIntegralNumber()) {
            throw new IllegalStateException("Odoo no devolvió el ID del registro creado en " + modelo);
        }
        return ids.get(0).asInt();
    }

    // ------------------------------------------------------------------
    // Clasificación firma/pago en Ventas (sin módulo propio ni Studio)
    // ------------------------------------------------------------------

    /** Campos propios (prefijo x_, permitidos en Odoo Community sin módulo) que rellena VetStock. */
    private static final String[][] CAMPOS_CLASIFICACION = {
            {"x_vetstock_pagado", "boolean", "VetStock: pago completo confirmado"},
            {"x_vetstock_firma_valida", "boolean", "VetStock: firma válida del veterinario asignado"},
            {"x_vetstock_estado", "char", "VetStock: estado logístico"},
    };
    private static final String FILTRO_PENDIENTES_FIRMA = "Pagados pendientes de firma (VetStock)";
    private static final String FILTRO_FIRMADOS_PAGADOS = "Firmados y pagados (VetStock)";
    private boolean clasificacionPreparada = false;

    /**
     * Crea (solo si no existen) los campos x_ en sale.order y dos filtros favoritos
     * compartidos en Ventas. No cambia vistas, permisos ni el estado comercial.
     */
    private void asegurarClasificacionVentas() {
        if (clasificacionPreparada) return;
        JsonNode modelos = buscar("ir.model", array().add(array().add("model").add("=").add("sale.order")));
        if (!modelos.isArray() || modelos.isEmpty()) {
            throw new IllegalStateException("Odoo no tiene el modelo sale.order (¿falta la app Ventas?)");
        }
        int modeloId = modelos.get(0).asInt();
        for (String[] campo : CAMPOS_CLASIFICACION) {
            ArrayNode dominio = array().add(array().add("model").add("=").add("sale.order"))
                    .add(array().add("name").add("=").add(campo[0]));
            if (buscar("ir.model.fields", dominio).isEmpty()) {
                ObjectNode valores = json.createObjectNode().put("name", campo[0]).put("ttype", campo[1])
                        .put("field_description", campo[2]).put("model_id", modeloId).put("state", "manual");
                crear("ir.model.fields", valores);
            }
        }
        String abiertos = "('x_vetstock_estado', 'not in', ['CANCELADO', 'BORRADOR'])";
        String pendientes = "[('x_vetstock_pagado', '=', True), ('x_vetstock_firma_valida', '=', False), " + abiertos + "]";
        String firmados = "[('x_vetstock_pagado', '=', True), ('x_vetstock_firma_valida', '=', True), " + abiertos + "]";
        asegurarFiltro(FILTRO_PENDIENTES_FIRMA, pendientes);
        asegurarFiltro(FILTRO_FIRMADOS_PAGADOS, firmados);
        // Accesos directos en Ventas → Pedidos. El menú «Pedidos» estándar oculta los presupuestos
        // (estado borrador), que es como llegan los pedidos de VetStock: por eso tienen su propia acción.
        int vista = asegurarVistaClasificacion();
        asegurarMenu(FILTRO_PENDIENTES_FIRMA, pendientes, vista, 90);
        asegurarMenu(FILTRO_FIRMADOS_PAGADOS, firmados, vista, 91);
        clasificacionPreparada = true;
    }

    private static final String NOMBRE_VISTA = "vetstock.sale.order.clasificacion.list";

    /** Lista propia con las columnas de VetStock (no modifica las vistas estándar de Ventas). */
    private int asegurarVistaClasificacion() {
        JsonNode existentes = buscar("ir.ui.view", array().add(array().add("name").add("=").add(NOMBRE_VISTA)));
        if (!existentes.isEmpty()) return existentes.get(0).asInt();
        String arch = "<list string=\"Pedidos VetStock\" create=\"0\">"
                + "<field name=\"name\"/><field name=\"client_order_ref\" string=\"Pedido VetStock\"/>"
                + "<field name=\"partner_id\"/><field name=\"date_order\"/>"
                + "<field name=\"amount_total\" sum=\"Total\"/>"
                + "<field name=\"x_vetstock_pagado\" string=\"Pagado\"/>"
                + "<field name=\"x_vetstock_firma_valida\" string=\"Firma válida\"/>"
                + "<field name=\"x_vetstock_estado\" string=\"Estado VetStock\"/>"
                + "<field name=\"state\" widget=\"badge\"/></list>";
        ObjectNode valores = json.createObjectNode().put("name", NOMBRE_VISTA).put("model", "sale.order")
                .put("type", "list").put("arch", arch);
        return crear("ir.ui.view", valores);
    }

    /** Acción + entrada de menú bajo Ventas → Pedidos con el mismo dominio que el favorito. */
    private void asegurarMenu(String nombre, String dominioTexto, int vistaId, int secuencia) {
        if (!buscar("ir.ui.menu", array().add(array().add("name").add("=").add(nombre))).isEmpty()) return;
        JsonNode padre = buscar("ir.model.data", array()
                .add(array().add("module").add("=").add("sale"))
                .add(array().add("name").add("=").add("sale_order_menu")));
        if (padre.isEmpty()) return; // sin el menú estándar de Ventas, quedan solo los favoritos
        int idPadre = leerRegistro("ir.model.data", padre.get(0).asInt(), "res_id").path("res_id").asInt();
        ObjectNode accion = json.createObjectNode().put("name", nombre).put("type", "ir.actions.act_window")
                .put("res_model", "sale.order").put("view_mode", "list,form").put("view_id", vistaId)
                .put("domain", dominioTexto).put("context", "{'create': False}");
        int idAccion = crear("ir.actions.act_window", accion);
        ObjectNode menu = json.createObjectNode().put("name", nombre).put("parent_id", idPadre)
                .put("action", "ir.actions.act_window," + idAccion).put("sequence", secuencia);
        crear("ir.ui.menu", menu);
    }

    /** Filtro favorito COMPARTIDO (sin usuario): lo ven todos los usuarios de Ventas. */
    private void asegurarFiltro(String nombre, String dominioTexto) {
        ArrayNode dominio = array().add(array().add("model_id").add("=").add("sale.order"))
                .add(array().add("name").add("=").add(nombre));
        if (!buscar("ir.filters", dominio).isEmpty()) return;
        ObjectNode valores = json.createObjectNode().put("name", nombre).put("model_id", "sale.order")
                .put("domain", dominioTexto).put("context", "{}").put("is_default", false);
        valores.put("user_id", false);
        crear("ir.filters", valores);
    }

    /** Copia en el sale.order la clasificación calculada por VetStock (si la ficha la trae). */
    private void aplicarClasificacion(ObjectNode valores, FichaPedido ficha) {
        if (ficha.pagoCompleto() == null || ficha.firmaValida() == null) return;
        asegurarClasificacionVentas();
        valores.put("x_vetstock_pagado", ficha.pagoCompleto());
        valores.put("x_vetstock_firma_valida", ficha.firmaValida());
        valores.put("x_vetstock_estado", ficha.estadoPedido());
    }

    @Override
    public Resultado probarConexion() {
        try {
            uid();
            return Resultado.ok(null, "Conexión correcta con Odoo (" + url + ", base de datos " + db + ")");
        } catch (Exception e) {
            return Resultado.fallo(e.getMessage());
        }
    }

    /**
     * Obtiene la compañía del usuario API y comprueba el modo de redondeo sin
     * modificar la compañía (en particular, sin cambiar su moneda).
     */
    private ConfiguracionEmpresa empresaActual() {
        synchronized (cerrojo) {
            if (empresaActual != null) {
                return empresaActual;
            }
            JsonNode usuarioApi = leerRegistro("res.users", uid(), "company_id");
            Integer idEmpresa = idRelacion(usuarioApi.path("company_id"));
            if (idEmpresa == null) {
                throw new IllegalStateException("El usuario API de Odoo no tiene una compañía actual configurada");
            }
            JsonNode empresa = leerRegistro("res.company", idEmpresa, "currency_id",
                    "account_fiscal_country_id", "tax_calculation_rounding_method");
            String redondeo = empresa.path("tax_calculation_rounding_method").asText();
            if (!"round_per_line".equals(redondeo)) {
                throw new IllegalStateException("La compañía Odoo debe usar redondeo de impuestos por línea "
                        + "(round_per_line); se ha encontrado " + (redondeo.isBlank() ? "sin configurar" : redondeo)
                        + ". VetStock no modifica esa configuración global automáticamente");
            }
            Integer paisFiscal = idRelacion(empresa.path("account_fiscal_country_id"));
            if (paisFiscal == null) {
                throw new IllegalStateException("La compañía Odoo no tiene account_fiscal_country_id; no se puede crear "
                        + "el impuesto VetStock de forma segura");
            }
            empresaActual = new ConfiguracionEmpresa(idEmpresa, paisFiscal);
            return empresaActual;
        }
    }

    /**
     * Busca o crea solamente el impuesto propio de VetStock y valida de forma
     * estricta una configuración existente. Nunca corrige ni reutiliza otro
     * impuesto de Odoo.
     */
    private int asegurarImpuestoVenta4() {
        synchronized (cerrojo) {
            if (impuestoVenta4Id != null) {
                return impuestoVenta4Id;
            }
            ConfiguracionEmpresa empresa = empresaActual();
            ArrayNode dominio = array()
                    .add(array().add("name").add("=").add(NOMBRE_IMPUESTO_IVA4))
                    .add(array().add("company_id").add("=").add(empresa.id()));
            JsonNode ids = buscar("account.tax", dominio);
            if (!ids.isArray() || ids.size() > 1) {
                throw new IllegalStateException("Hay varios impuestos propios llamados '" + NOMBRE_IMPUESTO_IVA4
                        + "' en la compañía API; corrige los duplicados antes de sincronizar");
            }
            int id;
            if (ids.isEmpty()) {
                ObjectNode valores = json.createObjectNode();
                valores.put("name", NOMBRE_IMPUESTO_IVA4);
                valores.put("amount", PORCENTAJE_IVA4);
                valores.put("amount_type", "percent");
                valores.put("type_tax_use", "sale");
                // En Odoo 18 el campo se acepta al crear aunque sea de solo lectura después.
                valores.put("company_id", empresa.id());
                valores.put("country_id", empresa.paisFiscalId());
                valores.put("price_include_override", "tax_excluded");
                valores.put("include_base_amount", false);
                valores.put("active", true);
                valores.put("tax_group_id", asegurarGrupoIva4(empresa));
                id = crear("account.tax", valores);
            } else {
                id = ids.get(0).asInt();
            }
            // Primero validar el tipo real. Cambiar su grupo solo corrige la
            // etiqueta del resumen; jamás se fuerza el porcentaje ni se toca
            // el grupo Tax 15% compartido con impuestos ajenos a VetStock.
            JsonNode impuesto = validarImpuestoVenta4(id, empresa);
            int grupoId = asegurarGrupoIva4(empresa);
            if (!Integer.valueOf(grupoId).equals(idRelacion(impuesto.path("tax_group_id")))) {
                escribir("account.tax", id, json.createObjectNode().put("tax_group_id", grupoId));
            }
            impuestoVenta4Id = id;
            return id;
        }
    }

    /** Grupo propio para que el resumen no herede la etiqueta fiscal del 15%. */
    private int asegurarGrupoIva4(ConfiguracionEmpresa empresa) {
        synchronized (cerrojo) {
            if (grupoVenta4Id != null) {
                return grupoVenta4Id;
            }
            ArrayNode dominio = array()
                    .add(array().add("name").add("=").add(NOMBRE_GRUPO_IVA4))
                    .add(array().add("company_id").add("=").add(empresa.id()));
            JsonNode ids = buscar("account.tax.group", dominio);
            if (!ids.isArray() || ids.size() > 1) {
                throw new IllegalStateException("Hay varios grupos fiscales propios '" + NOMBRE_GRUPO_IVA4
                        + "' en la compañía API; revisa sus duplicados");
            }
            int id;
            if (ids.isEmpty()) {
                ObjectNode valores = json.createObjectNode().put("name", NOMBRE_GRUPO_IVA4)
                        .put("company_id", empresa.id()).put("country_id", empresa.paisFiscalId());
                id = crear("account.tax.group", valores);
            } else {
                id = ids.get(0).asInt();
            }
            JsonNode grupo = leerRegistro("account.tax.group", id, "name", "company_id", "country_id");
            if (!NOMBRE_GRUPO_IVA4.equals(grupo.path("name").asText())
                    || !Integer.valueOf(empresa.id()).equals(idRelacion(grupo.path("company_id")))
                    || !Integer.valueOf(empresa.paisFiscalId()).equals(idRelacion(grupo.path("country_id")))) {
                throw new IllegalStateException("El grupo fiscal propio '" + NOMBRE_GRUPO_IVA4
                        + "' no coincide con la compañía y el país fiscal; no se modificará un grupo ajeno");
            }
            grupoVenta4Id = id;
            return id;
        }
    }

    private JsonNode validarImpuestoVenta4(int id, ConfiguracionEmpresa empresa) {
        JsonNode impuesto = leerRegistro("account.tax", id, "name", "company_id", "country_id", "amount",
                "amount_type", "type_tax_use", "price_include", "price_include_override", "include_base_amount",
                "active", "tax_group_id");
        List<String> errores = new ArrayList<>();
        if (!NOMBRE_IMPUESTO_IVA4.equals(impuesto.path("name").asText())) {
            errores.add("nombre distinto");
        }
        if (!Integer.valueOf(empresa.id()).equals(idRelacion(impuesto.path("company_id")))) {
            errores.add("no pertenece a la compañía API");
        }
        if (!Integer.valueOf(empresa.paisFiscalId()).equals(idRelacion(impuesto.path("country_id")))) {
            errores.add("country_id no coincide con el país fiscal de la compañía");
        }
        if (decimal(impuesto.path("amount"), "amount del impuesto").compareTo(PORCENTAJE_IVA4) != 0) {
            errores.add("amount no es 4.00");
        }
        if (!"percent".equals(impuesto.path("amount_type").asText())) {
            errores.add("amount_type no es percent");
        }
        if (!"sale".equals(impuesto.path("type_tax_use").asText())) {
            errores.add("type_tax_use no es sale");
        }
        if (!"tax_excluded".equals(impuesto.path("price_include_override").asText())) {
            errores.add("price_include_override no es tax_excluded");
        }
        if (impuesto.path("price_include").asBoolean(false)) {
            errores.add("price_include está activado");
        }
        if (impuesto.path("include_base_amount").asBoolean(false)) {
            errores.add("include_base_amount está activado");
        }
        if (!impuesto.path("active").asBoolean(false)) {
            errores.add("está inactivo");
        }
        if (!errores.isEmpty()) {
            throw new IllegalStateException("La configuración del impuesto propio '" + NOMBRE_IMPUESTO_IVA4
                    + "' no es válida (" + String.join("; ", errores)
                    + "). No se modificará ni se sustituirá por otro impuesto");
        }
        return impuesto;
    }

    /** Devuelve la moneda EUR existente y la activa si estaba archivada. */
    private int asegurarMonedaEur() {
        synchronized (cerrojo) {
            if (monedaEurId != null) {
                return monedaEurId;
            }
            JsonNode ids = buscar("res.currency", array().add(array().add("name").add("=").add(CODIGO_EUR)));
            if (!ids.isArray() || ids.size() != 1) {
                throw new IllegalStateException(ids.isArray() && ids.isEmpty()
                        ? "No existe la moneda EUR en Odoo; VetStock no crea monedas globales automáticamente"
                        : "Hay varias monedas EUR en Odoo; revisa su configuración antes de sincronizar");
            }
            int id = ids.get(0).asInt();
            JsonNode moneda = leerRegistro("res.currency", id, "name", "active");
            if (!CODIGO_EUR.equals(moneda.path("name").asText())) {
                throw new IllegalStateException("La moneda localizada como EUR tiene una configuración inválida");
            }
            if (!moneda.path("active").asBoolean(false)) {
                ObjectNode valores = json.createObjectNode();
                valores.put("active", true);
                escribir("res.currency", id, valores);
            }
            monedaEurId = id;
            return id;
        }
    }

    /** Crea o valida la lista propia, sin tocar currency_id de res.company. */
    private int asegurarListaPrecioEur(ConfiguracionEmpresa empresa) {
        synchronized (cerrojo) {
            if (listaPrecioEurId != null) {
                return listaPrecioEurId;
            }
            int eurId = asegurarMonedaEur();
            ArrayNode dominio = array()
                    .add(array().add("name").add("=").add(NOMBRE_LISTA_EUR))
                    .add(array().add("company_id").add("=").add(empresa.id()));
            JsonNode ids = buscar("product.pricelist", dominio);
            if (!ids.isArray() || ids.size() > 1) {
                throw new IllegalStateException("Hay varias listas de precios propias '" + NOMBRE_LISTA_EUR
                        + "' para la compañía API; corrige los duplicados antes de sincronizar");
            }
            int id;
            if (ids.isEmpty()) {
                ObjectNode valores = json.createObjectNode();
                valores.put("name", NOMBRE_LISTA_EUR);
                valores.put("company_id", empresa.id());
                valores.put("currency_id", eurId);
                valores.put("active", true);
                id = crear("product.pricelist", valores);
            } else {
                id = ids.get(0).asInt();
            }
            JsonNode lista = leerRegistro("product.pricelist", id, "name", "company_id", "currency_id", "active");
            List<String> errores = new ArrayList<>();
            if (!NOMBRE_LISTA_EUR.equals(lista.path("name").asText())) {
                errores.add("nombre distinto");
            }
            if (!Integer.valueOf(empresa.id()).equals(idRelacion(lista.path("company_id")))) {
                errores.add("no pertenece a la compañía API");
            }
            if (!Integer.valueOf(eurId).equals(idRelacion(lista.path("currency_id")))) {
                errores.add("no usa EUR");
            }
            if (!lista.path("active").asBoolean(false)) {
                errores.add("está inactiva");
            }
            if (!errores.isEmpty()) {
                throw new IllegalStateException("La lista de precios propia '" + NOMBRE_LISTA_EUR
                        + "' no es válida (" + String.join("; ", errores) + ")");
            }
            listaPrecioEurId = id;
            return id;
        }
    }

    @Override
    public Resultado crearProducto(FichaProducto ficha) {
        try {
            ConfiguracionEmpresa empresa = empresaActual();
            int impuestoId = asegurarImpuestoVenta4();
            JsonNode ids = buscar("product.template",
                    array().add(array().add("default_code").add("=").add(ficha.codigo())));
            if (!ids.isArray() || ids.size() > 1) {
                return Resultado.fallo("Hay varios productos con código " + ficha.codigo()
                        + "; revisa los duplicados en Odoo");
            }

            ObjectNode valores = json.createObjectNode();
            valores.put("name", ficha.nombre());
            if (ficha.descripcion() != null) {
                valores.put("description", ficha.descripcion());
            }

            if (!ids.isEmpty()) {
                int id = ids.get(0).asInt();
                JsonNode producto = leerRegistro("product.template", id, "taxes_id");
                valores.set("taxes_id", reemplazarImpuestosVentaDeEmpresa(producto.path("taxes_id"), empresa, impuestoId));
                // No se escribe list_price ni default_code: se preservan precio neto, código e id existentes.
                escribir("product.template", id, valores);
                return Resultado.ok(id, "Producto actualizado en Odoo; conserva precio neto e impuestos de otras compañías");
            }

            valores.put("default_code", ficha.codigo());
            valores.put("list_price", ficha.precio());
            valores.set("taxes_id", comandoM2m(List.of(impuestoId)));
            return Resultado.ok(crear("product.template", valores), "Producto creado en Odoo con IVA VetStock 4% no incluido");
        } catch (Exception e) {
            return Resultado.fallo(e.getMessage());
        }
    }

    /**
     * Conserva impuestos de otras compañías (también los globales) y reemplaza
     * todos los de la compañía API por el impuesto propio de VetStock.
     */
    private ArrayNode reemplazarImpuestosVentaDeEmpresa(JsonNode taxesId, ConfiguracionEmpresa empresa, int impuestoPropio) {
        List<Integer> ids = idsRelacionados(taxesId);
        List<Integer> conservar = new ArrayList<>();
        if (!ids.isEmpty()) {
            ObjectNode kwargs = json.createObjectNode();
            kwargs.set("fields", array().add("company_id"));
            ArrayNode registros = (ArrayNode) ejecutar("account.tax", "read", array().add(idsComoArray(ids)), kwargs);
            if (registros.size() != ids.size()) {
                throw new IllegalStateException("No se han podido leer todos los impuestos de venta del producto");
            }
            for (JsonNode impuesto : registros) {
                int id = impuesto.path("id").asInt();
                Integer empresaImpuesto = idRelacion(impuesto.path("company_id"));
                if (!Integer.valueOf(empresa.id()).equals(empresaImpuesto)) {
                    conservar.add(id);
                }
            }
        }
        conservar.add(impuestoPropio);
        return comandoM2m(conservar);
    }

    private ArrayNode comandoM2m(List<Integer> ids) {
        ArrayNode comando = array().add(6).add(0);
        ArrayNode idsComando = comando.addArray();
        ids.forEach(idsComando::add);
        return array().add(comando);
    }

    private ArrayNode idsComoArray(List<Integer> ids) {
        ArrayNode resultado = array();
        ids.forEach(resultado::add);
        return resultado;
    }

    @Override
    public Resultado crearCliente(FichaCliente ficha) {
        try {
            Integer id = ficha.odooId();
            if (id == null) {
                ArrayNode nombres = array();
                variantesNombre(ficha.nombre()).forEach(nombres::add);
                ArrayNode dominio = array().add(array().add("name").add("in").add(nombres))
                        .add(array().add("is_company").add("=").add(true));
                JsonNode ids = buscar("res.partner", dominio);
                if (!ids.isArray() || ids.size() > 1) {
                    return Resultado.fallo("Hay varios contactos que coinciden con la clínica; revisa sus IDs antes de sincronizar");
                }
                if (!ids.isEmpty()) {
                    id = ids.get(0).asInt();
                }
            }
            String notas = "";
            if (id != null) {
                JsonNode contacto = leerRegistro("res.partner", id, "name", "comment");
                JsonNode comentario = contacto.path("comment");
                if (comentario.isTextual()) {
                    notas = comentario.asText();
                }
            }
            ObjectNode valores = valoresCliente(ficha, notas);
            if (id != null) {
                escribir("res.partner", id, valores);
                return Resultado.ok(id, "Contacto actualizado en Odoo; acentos y responsable corregidos, mismo ID");
            }
            valores.put("customer_rank", 1);
            return Resultado.ok(crear("res.partner", valores), "Clínica creada en Odoo con su responsable");
        } catch (Exception e) {
            return Resultado.fallo(e.getMessage());
        }
    }

    private Set<String> variantesNombre(String nombre) {
        Set<String> nombres = new LinkedHashSet<>();
        nombres.add(nombre);
        for (Charset charset : new Charset[]{StandardCharsets.ISO_8859_1, Charset.forName("windows-1252")}) {
            String variante = nombre;
            for (int i = 0; i < 2; i++) {
                variante = new String(variante.getBytes(StandardCharsets.UTF_8), charset);
                nombres.add(variante);
            }
        }
        return nombres;
    }

    private ObjectNode valoresCliente(FichaCliente ficha, String notas) {
        ObjectNode valores = json.createObjectNode();
        valores.put("name", ficha.nombre());
        valores.put("is_company", true);
        if (ficha.direccion() != null) {
            valores.put("street", ficha.direccion());
        }
        if (ficha.telefono() != null) {
            valores.put("phone", ficha.telefono());
        }
        String responsable = ficha.veterinarioResponsable() == null
                ? "Sin veterinario responsable asignado" : ficha.veterinarioResponsable();
        valores.put("comment", actualizarNotas(notas, responsable));
        return valores;
    }

    private String actualizarNotas(String notas, String responsable) {
        String conservadas = notas.replaceAll(
                "(?s)<p>\\s*<strong>VetStock: veterinario responsable</strong>.*?</p>", "");
        if (conservadas.matches("(?s)\\s*(<p>)?Veterinario responsable: [^<\\r\\n]*(</p>)?\\s*")) {
            conservadas = "";
        }
        return conservadas + "<p><strong>VetStock: veterinario responsable</strong>: "
                + escaparHtml(responsable) + "</p>";
    }

    private String escaparHtml(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Exporta (crea o actualiza) el pedido y, si en VetStock ya está PAGADO y con FIRMA VÁLIDA,
     * lo confirma en Odoo (Presupuesto → Pedido de venta) para que salga a preparar.
     * Es una regla fija, no una decisión «inteligente»: nadie tiene que pulsar «Confirm».
     */
    @Override
    public Resultado crearPedido(FichaPedido ficha) {
        Resultado exportado = exportarPedido(ficha);
        if (!exportado.exito() || exportado.odooId() == null) {
            return exportado;
        }
        try {
            return confirmarSiFirmadoYPagado(exportado, ficha);
        } catch (Exception e) {
            return Resultado.fallo("Pedido exportado, pero no se pudo confirmar en Odoo: " + e.getMessage());
        }
    }

    /**
     * Solo confirma presupuestos (draft/sent). Un pedido cancelado en Odoo no se reactiva y uno
     * ya confirmado no se toca, así que repetir la sincronización no tiene efectos (idempotente).
     */
    private Resultado confirmarSiFirmadoYPagado(Resultado exportado, FichaPedido ficha) {
        boolean listo = Boolean.TRUE.equals(ficha.pagoCompleto()) && Boolean.TRUE.equals(ficha.firmaValida())
                && !"CANCELADO".equals(ficha.estadoPedido());
        if (!listo) {
            return exportado;
        }
        int id = exportado.odooId();
        String estado = leerRegistro("sale.order", id, "state").path("state").asText();
        switch (estado) {
            case "draft", "sent" -> {
                ejecutar("sale.order", "action_confirm", array().add(array().add(id)), null);
                return Resultado.ok(id, exportado.detalle() + "; firmado y pagado: confirmado en Odoo como pedido de venta");
            }
            case "cancel" -> {
                return Resultado.ok(id, exportado.detalle() + "; está cancelado en Odoo, no se confirma automáticamente");
            }
            default -> {
                return exportado; // ya confirmado
            }
        }
    }

    private Resultado exportarPedido(FichaPedido ficha) {
        try {
            validarFichaFiscal(ficha);
            if (ficha.odooPedidoId() != null) {
                return actualizarPedidoExistente(ficha.odooPedidoId(), ficha,
                        "Pedido actualizado en Odoo; se conserva el mismo ID");
            }

            ArrayNode dominio = array().add(array().add("client_order_ref").add("=").add(ficha.referencia()))
                    .add(array().add("partner_id").add("=").add(ficha.odooClienteId()));
            JsonNode existentes = buscar("sale.order", dominio);
            if (!existentes.isArray() || existentes.size() > 1) {
                return Resultado.fallo("Hay varios pedidos con la referencia " + ficha.referencia() + " en Odoo");
            }
            if (!existentes.isEmpty()) {
                return actualizarPedidoExistente(existentes.get(0).asInt(), ficha,
                        "Pedido recuperado y actualizado en Odoo; se conserva el mismo ID");
            }

            ConfiguracionEmpresa empresa = empresaActual();
            TipoFiscal tipoFiscal = tipoFiscal(ficha);
            if (tipoFiscal == TipoFiscal.IVA4 && tieneImportesFiscales(ficha)) {
                validarIvaNuevoRedondeadoPorLinea(ficha);
            }
            Integer impuestoId = tipoFiscal == TipoFiscal.IVA4 ? asegurarImpuestoVenta4() : null;
            int listaPrecioId = asegurarListaPrecioEur(empresa);
            ArrayNode lineas = lineasPedido(ficha, impuestoId);

            ObjectNode valores = json.createObjectNode();
            valores.put("partner_id", ficha.odooClienteId());
            valores.put("company_id", empresa.id());
            valores.put("pricelist_id", listaPrecioId);
            valores.put("client_order_ref", ficha.referencia());
            valores.put("origin", "VetStock Manager");
            valores.set("order_line", lineas);
            valores.put("note", actualizarNotaPedido("", ficha));
            aplicarClasificacion(valores, ficha);
            int id = crear("sale.order", valores);
            if (tieneImportesFiscales(ficha)) {
                return verificarImportesPedido(id, ficha,
                        "Pedido de venta creado en Odoo (" + ficha.referencia() + ")", empresa, asegurarMonedaEur());
            }
            return Resultado.ok(id, "Pedido de venta creado en Odoo (" + ficha.referencia() + ")");
        } catch (Exception e) {
            return Resultado.fallo(e.getMessage());
        }
    }

    /**
     * Para un pedido ya existente solamente se modifica note: no se alteran
     * líneas, impuestos, lista de precios, moneda, stock ni estado comercial.
     */
    private Resultado actualizarPedidoExistente(int id, FichaPedido ficha, String detalle) {
        JsonNode pedido = leerRegistro("sale.order", id, "note");
        JsonNode nota = pedido.path("note");
        String anterior = nota.isTextual() ? nota.asText() : "";
        ObjectNode valores = json.createObjectNode();
        valores.put("note", actualizarNotaPedido(anterior, ficha));
        aplicarClasificacion(valores, ficha);
        escribir("sale.order", id, valores);
        if (!tieneImportesFiscales(ficha)) {
            return Resultado.ok(id, detalle);
        }
        ConfiguracionEmpresa empresa = empresaActual();
        return verificarImportesPedido(id, ficha, detalle, empresa, asegurarMonedaEur());
    }

    private ArrayNode lineasPedido(FichaPedido ficha, Integer impuestoId) {
        ArrayNode lineas = array();
        if (ficha.lineas() == null || ficha.lineas().isEmpty()) {
            throw new IllegalArgumentException("El pedido no tiene líneas");
        }
        for (LineaPedido linea : ficha.lineas()) {
            JsonNode idVariante = buscarVariante(linea.codigoProducto());
            if (idVariante == null) {
                throw new IllegalArgumentException("El producto " + linea.codigoProducto() + " no existe en Odoo");
            }
            ObjectNode valores = json.createObjectNode();
            valores.put("product_id", idVariante.asInt());
            valores.put("product_uom_qty", linea.cantidad());
            valores.put("price_unit", linea.precioUnitario());
            // Un vacío explícito evita que Odoo aplique el IVA 15% por defecto a históricos tasa 0.
            valores.set("tax_id", comandoM2m(impuestoId == null ? List.of() : List.of(impuestoId)));
            lineas.add(array().add(0).add(0).add(valores));
        }
        return lineas;
    }

    private TipoFiscal tipoFiscal(FichaPedido ficha) {
        if (ficha.ivaPorcentaje() == null || ficha.ivaPorcentaje().compareTo(BigDecimal.ZERO) == 0) {
            return TipoFiscal.HISTORICO_SIN_IVA;
        }
        if (ficha.ivaPorcentaje().compareTo(PORCENTAJE_IVA4) == 0) {
            return TipoFiscal.IVA4;
        }
        throw new IllegalArgumentException("La ficha de pedido tiene IVA " + ficha.ivaPorcentaje()
                + "%; Odoo sólo acepta 4.00% para pedidos nuevos o 0% para históricos sin exportar");
    }

    private void validarFichaFiscal(FichaPedido ficha) {
        int presentes = 0;
        if (ficha.baseImponible() != null) {
            presentes++;
        }
        if (ficha.importeIva() != null) {
            presentes++;
        }
        if (ficha.total() != null) {
            presentes++;
        }
        if (presentes > 0 && presentes < 3) {
            throw new IllegalArgumentException("La ficha de pedido trae un desglose fiscal incompleto; "
                    + "base imponible, IVA y total deben recibirse juntos");
        }
        if (presentes == 3 && ficha.ivaPorcentaje() == null) {
            throw new IllegalArgumentException("La ficha de pedido trae importes fiscales pero no porcentaje de IVA");
        }
        if (presentes == 3 && !textoNoVacio(ficha.moneda())) {
            throw new IllegalArgumentException("La ficha de pedido trae importes fiscales pero no moneda");
        }
    }

    private boolean tieneImportesFiscales(FichaPedido ficha) {
        return ficha.baseImponible() != null && ficha.importeIva() != null && ficha.total() != null;
    }

    /**
     * Los pedidos nuevos de VetStock calculan el impuesto de cada línea antes
     * de sumarlo. Así coincide con el modo round_per_line exigido a Odoo y se
     * evita el error de redondear el 4% únicamente sobre la base agregada.
     * Los históricos a 0% no pasan por esta comprobación: sus importes firmados
     * o pagados se conservan tal como fueron guardados.
     */
    private void validarIvaNuevoRedondeadoPorLinea(FichaPedido ficha) {
        if (ficha.lineas() == null || ficha.lineas().isEmpty()) {
            throw new IllegalArgumentException("El pedido no tiene líneas");
        }
        BigDecimal ivaCalculado = BigDecimal.ZERO;
        for (LineaPedido linea : ficha.lineas()) {
            if (linea.precioUnitario() == null) {
                throw new IllegalArgumentException("La línea " + linea.codigoProducto() + " no tiene precio unitario neto");
            }
            BigDecimal baseLinea = linea.precioUnitario().multiply(BigDecimal.valueOf(linea.cantidad()));
            BigDecimal ivaLinea = baseLinea.multiply(PORCENTAJE_IVA4)
                    .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
            ivaCalculado = ivaCalculado.add(ivaLinea);
        }
        ivaCalculado = ivaCalculado.setScale(2, RoundingMode.HALF_UP);
        if (ivaCalculado.compareTo(ficha.importeIva().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("El IVA VetStock no respeta el redondeo por línea a dos decimales "
                    + "HALF_UP: calculado=" + dinero(ivaCalculado) + ", ficha=" + dinero(ficha.importeIva()));
        }
    }

    /**
     * Lee el pedido después de crearlo o actualizar la nota y compara todos los
     * importes con VetStock en dos decimales. Un pedido ya pagado nunca se
     * reescribe para intentar cuadrarlo: se devuelve un fallo explícito.
     */
    private Resultado verificarImportesPedido(int id, FichaPedido ficha, String detalle,
                                               ConfiguracionEmpresa empresa, int eurId) {
        JsonNode pedido = leerRegistro("sale.order", id, "company_id", "currency_id", "amount_untaxed",
                "amount_tax", "amount_total");
        List<String> diferencias = new ArrayList<>();
        Integer empresaPedido = idRelacion(pedido.path("company_id"));
        if (!Integer.valueOf(empresa.id()).equals(empresaPedido)) {
            diferencias.add("company_id Odoo=" + relacionComoTexto(pedido.path("company_id"))
                    + " y compañía API=" + empresa.id());
        }
        Integer monedaPedido = idRelacion(pedido.path("currency_id"));
        if (!Integer.valueOf(eurId).equals(monedaPedido)) {
            diferencias.add("currency_id Odoo=" + relacionComoTexto(pedido.path("currency_id"))
                    + " y se requiere EUR (id " + eurId + ")");
        }
        if (!CODIGO_EUR.equalsIgnoreCase(ficha.moneda())) {
            diferencias.add("VetStock informa moneda " + ficha.moneda() + " y se requiere EUR");
        }
        compararImporte(diferencias, "amount_untaxed", pedido.path("amount_untaxed"), ficha.baseImponible());
        compararImporte(diferencias, "amount_tax", pedido.path("amount_tax"), ficha.importeIva());
        compararImporte(diferencias, "amount_total", pedido.path("amount_total"), ficha.total());
        if (!diferencias.isEmpty()) {
            return Resultado.fallo("Comprobación fiscal fallida para sale.order " + id + ": "
                    + String.join("; ", diferencias)
                    + ". No se han reescrito líneas, impuestos, moneda ni pagos del pedido");
        }
        return Resultado.ok(id, detalle + "; importes y moneda EUR verificados");
    }

    private void compararImporte(List<String> diferencias, String campo, JsonNode odoo, BigDecimal vetstock) {
        BigDecimal importeOdoo = decimal(odoo, campo + " de sale.order");
        if (importeOdoo.setScale(2, RoundingMode.HALF_UP)
                .compareTo(vetstock.setScale(2, RoundingMode.HALF_UP)) != 0) {
            diferencias.add(campo + " Odoo=" + dinero(importeOdoo) + " y VetStock=" + dinero(vetstock));
        }
    }

    /**
     * Inserta o reemplaza únicamente el bloque gestionado por VetStock. Los
     * delimitadores textuales sobreviven al saneado HTML de Odoo y las notas
     * manuales se conservan literalmente.
     */
    private String actualizarNotaPedido(String notaActual, FichaPedido ficha) {
        String nota = notaActual == null ? "" : notaActual;
        String referencia = ficha.referencia() == null ? "" : ficha.referencia();
        String identificador = Pattern.quote(escaparHtml(referencia));
        Pattern bloqueExistente = Pattern.compile(
                "(?is)<p\\b[^>]*>\\s*<strong\\b[^>]*>\\s*VetStock Manager \\(" + identificador
                        + "\\)\\s*</strong>\\s*</p>.*?<p\\b[^>]*>\\s*Fin del resumen VetStock \\("
                        + identificador + "\\)\\s*</p>");
        String bloque = bloquePedido(ficha, referencia);
        nota = bloqueExistente.matcher(nota).replaceAll("");
        Pattern anterior = Pattern.compile("(?is)(?:<div\\b[^>]*>\\s*)?"
                + "<p\\b[^>]*>\\s*<strong\\b[^>]*>\\s*VetStock Manager\\s*</strong>\\s*</p>\\s*"
                + "<p\\b[^>]*>\\s*Pago: Confirmado</p>\\s*<p\\b[^>]*>\\s*Estado de firma: (?:Firmado|Por firmar)</p>"
                + ".*?<p\\b[^>]*>\\s*Estado VetStock del pedido: [^<]*</p>(?:\\s*</div>)?");
        nota = anterior.matcher(nota).replaceAll("");
        return nota.isBlank() ? bloque : nota + "\n" + bloque;
    }

    private String bloquePedido(FichaPedido ficha, String referencia) {
        boolean firmado = "FIRMADO".equalsIgnoreCase(ficha.estadoFirma());
        String firmante = textoNoVacio(ficha.firmanteNombre())
                ? escaparHtml(ficha.firmanteNombre()) : "Pendiente de firma";
        String rol = textoNoVacio(ficha.firmanteRol())
                ? " (" + escaparHtml(ficha.firmanteRol()) + ")" : "";
        String responsable = textoNoVacio(ficha.responsableNombre())
                ? escaparHtml(ficha.responsableNombre()) : "Sin responsable asignado";
        String colegiado = textoNoVacio(ficha.numeroColegiado())
                ? "<p>N.º colegiado (identificativo): " + escaparHtml(ficha.numeroColegiado()) + "</p>" : "";
        String estadoPedido = textoNoVacio(ficha.estadoPedido())
                ? escaparHtml(ficha.estadoPedido()) : "Sin estado";
        String desglose = tieneImportesFiscales(ficha) ? bloqueDesgloseFiscal(ficha) : "";

        return "<p><strong>VetStock Manager (" + escaparHtml(referencia) + ")</strong></p>"
                + "<p>Pago: Confirmado</p>"
                + "<p>Estado de firma: " + (firmado ? "Firmado" : "Por firmar") + "</p>"
                + "<p>Responsable: " + responsable + "</p>"
                + "<p>Firmante: " + firmante + (firmado ? rol : "") + "</p>"
                + colegiado
                + "<p>Estado VetStock del pedido: " + estadoPedido + "</p>"
                + desglose
                + "<p>Fin del resumen VetStock (" + escaparHtml(referencia) + ")</p>";
    }

    private String bloqueDesgloseFiscal(FichaPedido ficha) {
        String moneda = escaparHtml(ficha.moneda());
        String iva = ficha.ivaPorcentaje().setScale(2, RoundingMode.HALF_UP).toPlainString();
        return "<p>Desglose fiscal VetStock: neto " + dinero(ficha.baseImponible()) + " " + moneda
                + "; IVA " + iva + "% " + dinero(ficha.importeIva()) + " " + moneda
                + "; total " + dinero(ficha.total()) + " " + moneda + "</p>";
    }

    private boolean textoNoVacio(String texto) {
        return texto != null && !texto.isBlank();
    }

    private JsonNode buscarVariante(String codigo) {
        JsonNode plantillas = buscar("product.template",
                array().add(array().add("default_code").add("=").add(codigo)));
        if (!plantillas.isArray() || plantillas.size() != 1) {
            return null;
        }
        JsonNode variantes = buscar("product.product", array().add(
                array().add("product_tmpl_id").add("=").add(plantillas.get(0).asInt())));
        if (!variantes.isArray() || variantes.size() != 1) {
            return null;
        }
        return variantes.get(0);
    }

    private Integer idRelacion(JsonNode valor) {
        if (valor == null || valor.isMissingNode() || valor.isNull() || valor.isBoolean() && !valor.asBoolean()) {
            return null;
        }
        if (valor.isIntegralNumber()) {
            return valor.asInt();
        }
        if (valor.isArray() && !valor.isEmpty() && valor.get(0).isIntegralNumber()) {
            return valor.get(0).asInt();
        }
        return null;
    }

    private List<Integer> idsRelacionados(JsonNode valores) {
        List<Integer> ids = new ArrayList<>();
        if (valores != null && valores.isArray()) {
            for (JsonNode valor : valores) {
                if (valor.isIntegralNumber()) {
                    ids.add(valor.asInt());
                }
            }
        }
        return ids;
    }

    private String relacionComoTexto(JsonNode relacion) {
        Integer id = idRelacion(relacion);
        if (id == null) {
            return "sin valor";
        }
        if (relacion.isArray() && relacion.size() > 1 && relacion.get(1).isTextual()) {
            return relacion.get(1).asText() + " (id " + id + ")";
        }
        return "id " + id;
    }

    private BigDecimal decimal(JsonNode valor, String campo) {
        if (valor == null || valor.isMissingNode() || valor.isNull() || !valor.isNumber() && !valor.isTextual()) {
            throw new IllegalStateException("Odoo no devolvió un número válido para " + campo);
        }
        try {
            return new BigDecimal(valor.asText());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Odoo no devolvió un número válido para " + campo, e);
        }
    }

    private String dinero(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private enum TipoFiscal {
        IVA4,
        HISTORICO_SIN_IVA
    }

    private record ConfiguracionEmpresa(int id, int paisFiscalId) {
    }
}
