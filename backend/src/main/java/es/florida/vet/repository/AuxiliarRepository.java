package es.florida.vet.repository;

import es.florida.vet.entity.Auxiliar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuxiliarRepository extends JpaRepository<Auxiliar, Long> {
    Optional<Auxiliar> findByUsuarioUsername(String username);
}
