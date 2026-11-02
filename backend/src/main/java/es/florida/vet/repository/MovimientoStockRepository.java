package es.florida.vet.repository;

import es.florida.vet.entity.MovimientoStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {
    List<MovimientoStock> findAllByOrderByFechaDesc();
    List<MovimientoStock> findByProductoIdOrderByFechaDesc(Long productoId);
}
