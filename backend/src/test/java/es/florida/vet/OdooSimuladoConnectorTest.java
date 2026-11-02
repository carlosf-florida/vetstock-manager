package es.florida.vet;

import es.florida.vet.odoo.OdooConnector;
import es.florida.vet.odoo.OdooSimuladoConnector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del ADAPTADOR SIMULADO de Odoo: permite trabajar y demostrar
 * el proyecto sin tener un servidor Odoo encendido.
 */
class OdooSimuladoConnectorTest {

    private final OdooConnector odoo = new OdooSimuladoConnector();

    @Test
    @DisplayName("probarConexion en modo simulado responde con éxito")
    void probarConexion() {
        OdooConnector.Resultado r = odoo.probarConexion();
        assertTrue(r.exito());
        assertNull(r.odooId());
        assertTrue(r.detalle().contains("SIMULADO"));
    }

    @Test
    @DisplayName("crearProducto asigna un id y es idempotente por código")
    void crearProducto() {
        OdooConnector.Resultado r1 = odoo.crearProducto(
                new OdooConnector.FichaProducto("MED-001", "Amoxicilina", "Antibiótico", new BigDecimal("14.50")));
        OdooConnector.Resultado r2 = odoo.crearProducto(
                new OdooConnector.FichaProducto("MED-001", "Amoxicilina", "Antibiótico", new BigDecimal("14.50")));
        assertTrue(r1.exito());
        assertTrue(r2.exito());
        assertEquals(r1.odooId(), r2.odooId(), "el mismo código no debe duplicarse");
    }

    @Test
    @DisplayName("crearCliente asigna un id distinto por nombre")
    void crearCliente() {
        OdooConnector.Resultado a = odoo.crearCliente(
                new OdooConnector.FichaCliente("Clínica A", "C/ Falsa 123", "600111222"));
        OdooConnector.Resultado b = odoo.crearCliente(
                new OdooConnector.FichaCliente("Clínica B", "C/ Cierta 1", "600333444"));
        assertTrue(a.exito());
        assertTrue(b.exito());
        assertNotEquals(a.odooId(), b.odooId());
    }

    @Test
    @DisplayName("crearPedido falla si un producto no está sincronizado antes")
    void crearPedidoFallo() {
        OdooConnector.Resultado r = odoo.crearPedido(new OdooConnector.FichaPedido(
                "VSM-1", 1, List.of(new OdooConnector.LineaPedido("NO-EXISTE", 2, new BigDecimal("10.00")))));
        assertFalse(r.exito());
        assertNull(r.odooId());
    }

    @Test
    @DisplayName("crearPedido funciona con los productos ya sincronizados")
    void crearPedidoOk() {
        odoo.crearProducto(new OdooConnector.FichaProducto("MED-001", "Amoxicilina", "", new BigDecimal("14.50")));
        odoo.crearCliente(new OdooConnector.FichaCliente("Clínica San Roque", "", ""));
        OdooConnector.Resultado r = odoo.crearPedido(new OdooConnector.FichaPedido(
                "VSM-2", 1, List.of(new OdooConnector.LineaPedido("MED-001", 5, new BigDecimal("14.50")))));
        assertTrue(r.exito());
        assertNotNull(r.odooId());
        assertTrue(r.detalle().contains("Pedido de venta"));
    }
}
