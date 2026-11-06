package es.florida.vet.dto;

import es.florida.vet.entity.SolicitudAlta;

import java.time.LocalDateTime;

/** Solicitud vista por administración (nunca se devuelve en rutas públicas). accesoActivo: null si aún no hay cuenta. */
public record SolicitudAltaResponse(Long id, String referencia, LocalDateTime fechaCreacion, String clinicaNombre,
                                    String identificadorFiscal, String email, String telefono, String localidad,
                                    String vetNombre, String vetColegiado, String vetEmail,
                                    String estadoRevision, String revisadoPor, LocalDateTime fechaRevision,
                                    String observacionesRevision, String estadoAlta, String mensajeAlta,
                                    Long clinicaId, Long veterinarioId, Integer odooId, boolean odooPendiente,
                                    String odooUltimoError, Boolean accesoActivo) {

    public static SolicitudAltaResponse desde(SolicitudAlta s, Boolean accesoActivo) {
        return new SolicitudAltaResponse(s.getId(), s.getReferencia(), s.getFechaCreacion(), s.getClinicaNombre(),
                s.getIdentificadorFiscal(), s.getEmail(), s.getTelefono(), s.getLocalidad(),
                s.getVetNombre(), s.getVetColegiado(), s.getVetEmail(),
                s.getEstadoRevision().name(), s.getRevisadoPor(), s.getFechaRevision(),
                s.getObservacionesRevision(), s.getEstadoAlta().name(), s.getMensajeAlta(),
                s.getClinicaId(), s.getVeterinarioId(), s.getOdooId(), s.isOdooPendiente(),
                s.getOdooUltimoError(), accesoActivo);
    }
}
