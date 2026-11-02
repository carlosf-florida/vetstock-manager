package es.florida.vet.repository;

import es.florida.vet.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findAllByOrderByFechaCreacionDesc();

    List<Pedido> findByClinicaIdOrderByFechaCreacionDesc(Long clinicaId);

    List<Pedido> findByEstadoOrderByFechaCreacionDesc(es.florida.vet.enums.EstadoPedido estado);

    List<Pedido> findByClinicaIdAndEstadoOrderByFechaCreacionDesc(Long clinicaId, es.florida.vet.enums.EstadoPedido estado);

    long countByEstado(es.florida.vet.enums.EstadoPedido estado);
}
