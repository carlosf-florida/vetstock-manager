package es.florida.vet.repository;

import es.florida.vet.entity.ActivacionCuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ActivacionCuentaRepository extends JpaRepository<ActivacionCuenta, Long> {

    Optional<ActivacionCuenta> findByTokenHash(String tokenHash);

    /** Al generar un enlace nuevo, los anteriores dejan de valer. */
    @Modifying
    @Query("UPDATE ActivacionCuenta a SET a.usada = CURRENT_TIMESTAMP WHERE a.usuario.id = :usuarioId AND a.usada IS NULL")
    void anularAnteriores(Long usuarioId);
}
