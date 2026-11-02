package es.florida.vet.controller;

import es.florida.vet.entity.OdooLog;
import es.florida.vet.odoo.OdooSincronizacionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Integración con el ERP Odoo. Solo ADMIN (lo protege SecurityConfig).
 *
 * Con  vetstock.odoo.modo=SIMULADO  usa un adaptador simulado (sin servidor);
 * con  vetstock.odoo.modo=REAL  se conecta por JSON-RPC a un servidor Odoo.
 */
@RestController
@RequestMapping("/api/odoo")
public class OdooController {

    private final OdooSincronizacionService odooService;

    public OdooController(OdooSincronizacionService odooService) {
        this.odooService = odooService;
    }

    @GetMapping("/estado")
    public Map<String, Object> estado() {
        return odooService.estado();
    }

    @PostMapping("/probar-conexion")
    public Map<String, Object> probarConexion() {
        return odooService.probarConexion();
    }

    @PostMapping("/sincronizar/productos")
    public Map<String, Object> sincronizarProductos() {
        return odooService.sincronizarProductos();
    }

    @PostMapping("/sincronizar/clientes")
    public Map<String, Object> sincronizarClientes() {
        return odooService.sincronizarClientes();
    }

    @PostMapping("/pedidos/{id}/exportar")
    public Map<String, Object> exportarPedido(@PathVariable Long id) {
        return odooService.enviarPedido(id);
    }

    @GetMapping("/log")
    public List<OdooLog> log() {
        return odooService.log();
    }
}
