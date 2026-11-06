package es.florida.vet.repository;

import es.florida.vet.entity.Veterinario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VeterinarioRepository extends JpaRepository<Veterinario, Long> {
    Optional<Veterinario> findByUsuarioUsername(String username);
    List<Veterinario> findAllByOrderByIdAsc();
    List<Veterinario> findByClinicaId(Long clinicaId);
    boolean existsByNumeroColegiado(String numeroColegiado);
    Optional<Veterinario> findByNumeroColegiado(String numeroColegiado);
}
