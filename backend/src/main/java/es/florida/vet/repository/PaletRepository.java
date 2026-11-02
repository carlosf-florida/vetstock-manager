package es.florida.vet.repository;

import es.florida.vet.entity.Palet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaletRepository extends JpaRepository<Palet, Long> {
    List<Palet> findAllByOrderByIdDesc();
    List<Palet> findByDestinoId(Long destinoId);
}
