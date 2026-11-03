package es.florida.vet.repository;

import es.florida.vet.entity.Destino;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DestinoRepository extends JpaRepository<Destino, Long> {
    java.util.Optional<Destino> findByCodigo(String codigo);
}
