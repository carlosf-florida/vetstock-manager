package es.florida.vet.repository;

import es.florida.vet.entity.SolicitudAlta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SolicitudAltaRepository extends JpaRepository<SolicitudAlta, Long> {

    boolean existsByReferencia(String referencia);

    Optional<SolicitudAlta> findByReferencia(String referencia);

    Optional<SolicitudAlta> findFirstByHuellaAndFechaCreacionAfterOrderByIdDesc(String huella, LocalDateTime desde);

    List<SolicitudAlta> findAllByOrderByIdDesc();

    List<SolicitudAlta> findByOdooPendienteTrue();

    /** Bloquea la fila: dos aprobaciones a la vez no pueden crear dos clínicas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SolicitudAlta s WHERE s.id = :id")
    Optional<SolicitudAlta> bloquear(Long id);
}
