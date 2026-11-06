package es.florida.vet.repository;

import java.util.Optional;
import es.florida.vet.entity.Clinica;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicaRepository extends JpaRepository<Clinica, Long> {

    Optional<Clinica> findByIdentificadorFiscal(String identificadorFiscal);

    Optional<Clinica> findFirstByNombreIgnoreCase(String nombre);
}
