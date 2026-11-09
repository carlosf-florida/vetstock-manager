package es.florida.vet.repository;

import es.florida.vet.entity.SolicitudDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SolicitudDocumentoRepository extends JpaRepository<SolicitudDocumento, Long> {

    /** Datos del documento vigente sin cargar la imagen (para listados y comprobaciones). */
    record Resumen(Long id, Long solicitudId, String nombreOriginal, String tipo, int tamano, Integer odooAdjuntoId) {
    }

    @Query("SELECT new es.florida.vet.repository.SolicitudDocumentoRepository$Resumen(d.id, d.solicitudId, d.nombreOriginal, d.tipo, d.tamano, d.odooAdjuntoId)"
            + " FROM SolicitudDocumento d WHERE d.vigente = true")
    List<Resumen> resumenesVigentes();

    @Query("SELECT new es.florida.vet.repository.SolicitudDocumentoRepository$Resumen(d.id, d.solicitudId, d.nombreOriginal, d.tipo, d.tamano, d.odooAdjuntoId)"
            + " FROM SolicitudDocumento d WHERE d.vigente = true AND d.solicitudId = :solicitudId")
    Optional<Resumen> resumenVigente(Long solicitudId);

    Optional<SolicitudDocumento> findFirstBySolicitudIdAndVigenteTrue(Long solicitudId);

    /** Al sustituirlo, el anterior se conserva pero deja de ser el vigente. */
    @Modifying
    @Query("UPDATE SolicitudDocumento d SET d.vigente = false WHERE d.solicitudId = :solicitudId AND d.vigente = true")
    void retirarVigente(Long solicitudId);

    @Modifying
    @Query("UPDATE SolicitudDocumento d SET d.odooAdjuntoId = :adjuntoId WHERE d.id = :id")
    void registrarAdjuntoOdoo(Long id, Integer adjuntoId);
}
