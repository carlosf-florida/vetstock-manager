package es.florida.vet.odoo;

import java.math.BigDecimal;
import java.util.List;

/**
 * ADAPTADOR de integración con Odoo (ERP).
 *
 * Define QUÉ se puede hacer contra el ERP sin decir CÓMO: hay dos
 * implementaciones:
 *
 *  - OdooJsonRpcConnector: real, contra un servidor Odoo (JSON-RPC).
 *  - OdooSimuladoConnector: para el prototipo sin servidor Odoo.
 *
 * El resto de la aplicación (servicios, controladores) solo conoce esta
 * interfaz, igual que con el adaptador de pagos.
 */
public interface OdooConnector {

    /** Resultado genérico de una operación contra Odoo. */
    record Resultado(boolean exito, Integer odooId, String detalle) {
        public static Resultado ok(Integer id, String detalle) { return new Resultado(true, id, detalle); }
        public static Resultado fallo(String detalle) { return new Resultado(false, null, detalle); }
    }

    record FichaProducto(String codigo, String nombre, String descripcion, BigDecimal precio) {
    }

    record FichaCliente(String nombre, String direccion, String telefono, String veterinarioResponsable,
                        Integer odooId) {
        public FichaCliente(String nombre, String direccion, String telefono, String veterinarioResponsable) {
            this(nombre, direccion, telefono, veterinarioResponsable, null);
        }

        public FichaCliente(String nombre, String direccion, String telefono) {
            this(nombre, direccion, telefono, null, null);
        }
    }

    record LineaPedido(String codigoProducto, int cantidad, BigDecimal precioUnitario) {
    }

    /**
     * Información comercial que VetStock conserva en la nota del sale.order.
     * Los campos de firma no alteran el estado nativo del pedido de Odoo: son
     * únicamente trazabilidad comercial de la autorización en VetStock.
     */
    record FichaPedido(String referencia, Integer odooClienteId, List<LineaPedido> lineas,
                       Integer odooPedidoId, String estadoFirma, String responsableNombre,
                       String firmanteNombre, String firmanteRol, String numeroColegiado,
                       String estadoPedido, BigDecimal ivaPorcentaje, BigDecimal baseImponible,
                       BigDecimal importeIva, BigDecimal total, String moneda,
                       Boolean pagoCompleto, Boolean firmaValida, String creadoPorNombre,
                       Integer bultos, String salida, BigDecimal pesoTotalKg, String direccionEntrega) {
        /** Sin clasificación firma/pago (null): no se escriben los campos x_vetstock_* en Odoo. */
        public FichaPedido(String referencia, Integer odooClienteId, List<LineaPedido> lineas,
                           Integer odooPedidoId, String estadoFirma, String responsableNombre,
                           String firmanteNombre, String firmanteRol, String numeroColegiado,
                           String estadoPedido, BigDecimal ivaPorcentaje, BigDecimal baseImponible,
                           BigDecimal importeIva, BigDecimal total, String moneda) {
            this(referencia, odooClienteId, lineas, odooPedidoId, estadoFirma, responsableNombre,
                    firmanteNombre, firmanteRol, numeroColegiado, estadoPedido, ivaPorcentaje, baseImponible,
                    importeIva, total, moneda, null, null, null, null, null, null, null);
        }

        public FichaPedido(String referencia, Integer odooClienteId, List<LineaPedido> lineas,
                           Integer odooPedidoId, String estadoFirma, String responsableNombre,
                           String firmanteNombre, String firmanteRol, String numeroColegiado,
                           String estadoPedido) {
            this(referencia, odooClienteId, lineas, odooPedidoId, estadoFirma, responsableNombre,
                    firmanteNombre, firmanteRol, numeroColegiado, estadoPedido, null, null, null, null, null);
        }

        /** Constructor histórico mantenido para consumidores y pruebas existentes. */
        public FichaPedido(String referencia, Integer odooClienteId, List<LineaPedido> lineas) {
            this(referencia, odooClienteId, lineas, null, null, null, null, null, null, null);
        }
    }

    /** Comprueba que hay conexión y credenciales válidas. */
    Resultado probarConexion();

    /** Crea el producto en Odoo y devuelve su id (product.template). */
    Resultado crearProducto(FichaProducto ficha);

    /** Crea el cliente (clínica) en Odoo y devuelve su id (res.partner). */
    Resultado crearCliente(FichaCliente ficha);

    /** Crea o actualiza el pedido de venta (sale.order) y devuelve su id. */
    Resultado crearPedido(FichaPedido ficha);
}
