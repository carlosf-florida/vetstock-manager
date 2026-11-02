package es.florida.vet.controller;

import es.florida.vet.dto.ProductoRequest;
import es.florida.vet.dto.ProductoResponse;
import es.florida.vet.service.ProductoService;
import es.florida.vet.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo de productos: lectura para todos, escritura solo ADMIN. */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public List<ProductoResponse> listar(@RequestParam(required = false) String busqueda,
                                         @RequestParam(required = false) String categoria,
                                         @RequestParam(required = false, defaultValue = "false") boolean soloBajoStock) {
        return productoService.listar(busqueda, categoria, soloBajoStock);
    }

    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable Long id) {
        return productoService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest datos, Authentication auth) {
        return productoService.crear(datos);
    }

    @PutMapping("/{id}")
    public ProductoResponse modificar(@PathVariable Long id, @Valid @RequestBody ProductoRequest datos,
                                      Authentication auth) {
        return productoService.modificar(id, datos);
    }

    @PatchMapping("/{id}/activar")
    public ProductoResponse activar(@PathVariable Long id, Authentication auth) {
        return productoService.cambiarActivo(id, true);
    }

    @PatchMapping("/{id}/desactivar")
    public ProductoResponse desactivar(@PathVariable Long id, Authentication auth) {
        return productoService.cambiarActivo(id, false);
    }
}
