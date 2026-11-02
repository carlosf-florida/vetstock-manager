package es.florida.vet.repository;

import es.florida.vet.entity.Pago;
import es.florida.vet.enums.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByReferencia(String referencia);
    List<Pago> findByPedidoIdOrderByFechaInicioDesc(Long pedidoId);
    List<Pago> findByEstado(EstadoPago estado);
}
