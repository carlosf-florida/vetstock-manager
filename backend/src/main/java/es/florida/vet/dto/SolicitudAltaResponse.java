package es.florida.vet.dto;

import es.florida.vet.entity.SolicitudAlta;
import es.florida.vet.repository.SolicitudDocumentoRepository;

import java.time.LocalDateTime;

/**
 * Solicitud vista por administración (nunca se devuelve en rutas públicas). accesoActivo: null si aún no hay cuenta.
 * documentoEnOdoo solo es true cuando Odoo ha confirmado que tiene el adjunto.
 */
public record SolicitudAltaResponse(Long id, String referencia, LocalDateTime fechaCreacion, String clinicaNombre,
                                    String identificadorFiscal, String email, String telefono, String localidad,
                                    String vetNombre, String vetColegiado, String vetEmail,
                                    String estadoRevision, String revisadoPor, LocalDateTime fechaRevision,
                                    String observacionesRevision, String estadoAlta, String mensajeAlta,
                                    Long clinicaId, Long veterinarioId, Integer odooId, boolean odooPendiente,
                                    String odooUltimoError, Boolean accesoActivo,
                                    String documentoEstado, String documentoRevisadoPor,
                                    LocalDateTime documentoFechaRevision, String documentoObservaciones,
                                    Long documentoId, String documentoNombre, Integer documentoTamano,
                                    boolean documentoEnOdoo) {

    public static SolicitudAltaResponse desde(SolicitudAlta s, Boolean accesoActivo, SolicitudDocumentoRepository.Resumen documento) {
        return new SolicitudAltaResponse(s.getId(), s.getReferencia(), s.getFechaCreacion(), s.getClinicaNombre(),
                s.getIdentificadorFiscal(), s.getEmail(), s.getTelefono(), s.getLocalidad(),
                s.getVetNombre(), s.getVetColegiado(), s.getVetEmail(),
                s.getEstadoRevision().name(), s.getRevisadoPor(), s.getFechaRevision(),
                s.getObservacionesRevision(), s.getEstadoAlta().name(), s.getMensajeAlta(),
                s.getClinicaId(), s.getVeterinarioId(), s.getOdooId(), s.isOdooPendiente(),
                s.getOdooUltimoError(), accesoActivo,
                s.getDocumentoEstado().name(), s.getDocumentoRevisadoPor(),
                s.getDocumentoFechaRevision(), s.getDocumentoObservaciones(),
                documento == null ? null : documento.id(), documento == null ? null : documento.nombreOriginal(),
                documento == null ? null : documento.tamano(), documento != null && documento.odooAdjuntoId() != null);
    }
}
