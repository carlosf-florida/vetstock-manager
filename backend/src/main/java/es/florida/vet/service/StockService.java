package es.florida.vet.service;

import es.florida.vet.dto.MovimientoRequest;
import es.florida.vet.dto.MovimientoResponse;
import es.florida.vet.entity.MovimientoStock;
import es.florida.vet.entity.Producto;
import es.florida.vet.entity.Usuario;
import es.florida.vet.enums.TipoMovimiento;
import es.florida.vet.repository.MovimientoStockRepository;
import es.florida.vet.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Entradas y salidas manuales de stock (mermas, recepciones...).
 * Las salidas del ciclo de pedidos las gestiona PedidoService al expedir.
 */
@Service
public class StockService {

    private final ProductoRepository productoRepository;
    private final MovimientoStockRepository movimientoRepository;
    private final SesionService sesionService;

    public StockService(ProductoRepository productoRepository,
                        MovimientoStockRepository movimientoRepository,
                        SesionService sesionService) {
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
        this.sesionService = sesionService;
    }

    @Transactional
    public MovimientoResponse entrada(MovimientoRequest datos, String username) {
        return registrar(datos, username, TipoMovimiento.ENTRADA);
    }

    @Transactional
    public MovimientoResponse salida(MovimientoRequest datos, String username) {
        return registrar(datos, username, TipoMovimiento.SALIDA);
    }

    private MovimientoResponse registrar(MovimientoRequest datos, String username, TipoMovimiento tipo) {
        Producto producto = productoRepository.findById(datos.productoId())
                .orElseThrow(() -> new es.florida.vet.exception.RecursoNoEncontradoException("El producto no existe"));
        Usuario usuario = sesionService.usuarioActual(username);

        if (tipo == TipoMovimiento.ENTRADA) {
            producto.registrarEntrada(datos.cantidad());
        } else {
            producto.registrarSalidaManual(datos.cantidad());
        }
        productoRepository.save(producto);
        MovimientoStock movimiento = new MovimientoStock(producto, usuario, null,
                tipo, datos.cantidad(), datos.motivo());
        return MovimientoResponse.desde(movimientoRepository.save(movimiento));
    }

    @Transactional(readOnly = true)
    public List<MovimientoResponse> historial(Long productoId) {
        List<MovimientoStock> movimientos = (productoId == null)
                ? movimientoRepository.findAllByOrderByFechaDesc()
                : movimientoRepository.findByProductoIdOrderByFechaDesc(productoId);
        return movimientos.stream().map(MovimientoResponse::desde).toList();
    }
}
