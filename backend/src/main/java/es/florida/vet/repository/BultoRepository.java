package es.florida.vet.repository;

import es.florida.vet.entity.Bulto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BultoRepository extends JpaRepository<Bulto, Long> {
    List<Bulto> findByPedidoId(Long pedidoId);
    List<Bulto> findByPaletId(Long paletId);
    List<Bulto> findAllByOrderByIdDesc();
}
