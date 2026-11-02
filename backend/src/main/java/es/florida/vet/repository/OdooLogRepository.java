package es.florida.vet.repository;

import es.florida.vet.entity.OdooLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OdooLogRepository extends JpaRepository<OdooLog, Long> {

    List<OdooLog> findTop50ByOrderByIdDesc();
}
