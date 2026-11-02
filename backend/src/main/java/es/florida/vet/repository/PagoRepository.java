package es.florida.vet.repository;

import es.florida.vet.entity.Pago;
import es.florida.vet.enums.EstadoPago;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByReferencia(String referencia);
    List<Pago> findByPedidoIdOrderByFechaInicioDesc(Long pedidoId);
    List<Pago> findByEstado(EstadoPago estado);

    /**
     * Bloquea la fila del pago (SELECT ... FOR UPDATE) mientras se procesa una
     * notificación: si llegan dos a la vez, la segunda espera y ya ve el pago resuelto.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pago p WHERE p.referencia = :referencia")
    Optional<Pago> bloquearPorReferencia(@Param("referencia") String referencia);

    boolean existsByPedidoIdAndEstadoAndFechaInicioAfter(Long pedidoId, EstadoPago estado, LocalDateTime desde);

    List<Pago> findByEstadoAndFechaInicioBefore(EstadoPago estado, LocalDateTime limite);
}
