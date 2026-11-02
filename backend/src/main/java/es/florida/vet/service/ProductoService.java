package es.florida.vet.service;

import es.florida.vet.dto.ProductoRequest;
import es.florida.vet.dto.ProductoResponse;
import es.florida.vet.entity.Producto;
import es.florida.vet.enums.CategoriaProducto;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Gestión del catálogo de productos. */
@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(String busqueda, String categoria, boolean soloBajoStock) {
        List<Producto> productos = productoRepository.findAllByOrderByNombreAsc();
        return productos.stream()
                .filter(p -> busqueda == null || busqueda.isBlank()
                        || p.getNombre().toLowerCase().contains(busqueda.toLowerCase())
                        || p.getCodigo().toLowerCase().contains(busqueda.toLowerCase()))
                .filter(p -> categoria == null || categoria.isBlank()
                        || p.getCategoria().name().equals(categoria))
                .filter(p -> !soloBajoStock || p.isStockBajo())
                .map(ProductoResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.desde(buscarProducto(id));
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest datos) {
        if (productoRepository.existsByCodigo(datos.codigo())) {
            throw new ReglaNegocioException("Ya existe un producto con el código " + datos.codigo());
        }
        Producto producto = new Producto();
        producto.setCodigo(datos.codigo());
        aplicarDatos(producto, datos);
        producto.setStockActual(datos.stockInicial());
        return ProductoResponse.desde(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse modificar(Long id, ProductoRequest datos) {
        Producto producto = buscarProducto(id);
        // El código no se puede duplicar, pero sí mantener el propio
        if (!producto.getCodigo().equals(datos.codigo())
                && productoRepository.existsByCodigo(datos.codigo())) {
            throw new ReglaNegocioException("Ya existe un producto con el código " + datos.codigo());
        }
        producto.setCodigo(datos.codigo());
        aplicarDatos(producto, datos);
        return ProductoResponse.desde(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse cambiarActivo(Long id, boolean activo) {
        Producto producto = buscarProducto(id);
        producto.setActivo(activo);
        return ProductoResponse.desde(productoRepository.save(producto));
    }

    private void aplicarDatos(Producto producto, ProductoRequest datos) {
        producto.setNombre(datos.nombre());
        producto.setDescripcion(datos.descripcion());
        try {
            producto.setCategoria(CategoriaProducto.valueOf(datos.categoria()));
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException("La categoría no es válida");
        }
        producto.setPrecio(datos.precio());
        producto.setStockMinimo(datos.stockMinimo());
    }

    private Producto buscarProducto(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El producto no existe"));
    }
}
