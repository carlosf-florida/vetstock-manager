package es.florida.vet.repository;

import es.florida.vet.entity.Firma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FirmaRepository extends JpaRepository<Firma, Long> {
    Optional<Firma> findByPedidoId(Long pedidoId);
}
