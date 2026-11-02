package es.florida.vet.odoo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptador SIMULADO de Odoo: mismo comportamiento que el real pero sin
 * servidor. Guarda los identificadores en memoria y asigna unos crecientes.
 * Sirve para desarrollar y para la demo cuando no hay servidor Odoo.
 *
 * Igual que el conector REAL, es idempotente: si el producto, cliente o pedido
 * ya existe, responde éxito con su identificador en lugar de duplicarlo.
 *
 * NO conecta con ningún ERP real; está etiquetado como simulación.
 */
@Component
@ConditionalOnProperty(name = "vetstock.odoo.modo", havingValue = "SIMULADO", matchIfMissing = true)
public class OdooSimuladoConnector implements OdooConnector {

    private final AtomicInteger secuencia = new AtomicInteger(100); // ids como Odoo (empiezan en 1, simulamos >100)
    private final Map<String, Integer> productos = new HashMap<>();  // código -> id en "Odoo"
    private final Map<String, Integer> clientes = new HashMap<>();   // nombre -> id en "Odoo"
    private final Map<String, Integer> pedidos = new HashMap<>();    // referencia + cliente -> id en "Odoo"

    @Override
    public Resultado probarConexion() {
        return Resultado.ok(null, "Modo SIMULADO: no hay servidor Odoo conectado");
    }

    @Override
    public Resultado crearProducto(FichaProducto ficha) {
        if (ficha.codigo() == null || ficha.codigo().isBlank()) {
            return Resultado.fallo("El producto no tiene código");
        }
        Integer id = productos.get(ficha.codigo());
        if (id != null) {
            return Resultado.ok(id, "El producto ya existía en Odoo (SIMULADO)");
        }
        id = secuencia.incrementAndGet();
        productos.put(ficha.codigo(), id);
        return Resultado.ok(id, "Producto creado en Odoo (SIMULADO)");
    }

    @Override
    public Resultado crearCliente(FichaCliente ficha) {
        if (ficha.nombre() == null || ficha.nombre().isBlank()) {
            return Resultado.fallo("El cliente no tiene nombre");
        }
        Integer id = clientes.get(ficha.nombre());
        if (id != null) {
            return Resultado.ok(id, "El cliente ya existía en Odoo (SIMULADO), responsable actualizado");
        }
        id = secuencia.incrementAndGet();
        clientes.put(ficha.nombre(), id);
        return Resultado.ok(id, "Cliente creado en Odoo (SIMULADO) con responsable: "
                + (ficha.veterinarioResponsable() == null ? "sin asignar" : ficha.veterinarioResponsable()));
    }

    @Override
    public synchronized Resultado crearPedido(FichaPedido ficha) {
        if (ficha.odooPedidoId() != null) {
            if (pedidos.containsValue(ficha.odooPedidoId())) {
                return Resultado.ok(ficha.odooPedidoId(),
                        "Pedido actualizado en Odoo (SIMULADO); se conserva el mismo ID");
            }
            return Resultado.fallo("El pedido vinculado (id " + ficha.odooPedidoId()
                    + ") no existe en Odoo (SIMULADO)");
        }
        if (ficha.odooClienteId() == null) {
            return Resultado.fallo("El cliente de la clínica no está sincronizado con Odoo");
        }

        String clavePedido = ficha.referencia() + "|" + ficha.odooClienteId();
        Integer existente = pedidos.get(clavePedido);
        if (existente != null) {
            return Resultado.ok(existente,
                    "Pedido recuperado y actualizado en Odoo (SIMULADO); se conserva el mismo ID");
        }
        if (ficha.lineas() == null || ficha.lineas().isEmpty()) {
            return Resultado.fallo("El pedido no tiene líneas");
        }
        for (LineaPedido linea : ficha.lineas()) {
            if (!productos.containsKey(linea.codigoProducto())) {
                return Resultado.fallo("El producto " + linea.codigoProducto() + " no existe en Odoo");
            }
        }

        int id = secuencia.incrementAndGet();
        pedidos.put(clavePedido, id);
        return Resultado.ok(id, "Pedido de venta creado en Odoo (SIMULADO)");
    }
}
