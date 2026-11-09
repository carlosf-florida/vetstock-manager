package es.florida.vet.enums;

/**
 * Revisión MANUAL del documento acreditativo. Ninguna comprobación automática decide que un
 * documento sea auténtico o suficiente: lo acepta una persona en Odoo.
 */
public enum EstadoDocumentoSolicitud {
    /** Solicitud anterior a la acreditación: necesita un documento o una excepción autorizada. */
    SIN_DOCUMENTO,
    /** Hay documento y nadie lo ha aceptado todavía (también tras sustituirlo). */
    PENDIENTE,
    ACEPTADO,
    /** Solicitud anterior aprobable sin documento por decisión expresa, con motivo registrado. */
    EXCEPCION;

    public boolean permiteAprobar() {
        return this == ACEPTADO || this == EXCEPCION;
    }
}
