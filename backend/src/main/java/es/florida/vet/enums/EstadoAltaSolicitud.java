package es.florida.vet.enums;

/**
 * Cómo va el alta de una solicitud aprobada, independiente de la revisión.
 * REQUIERE_REVISION: hay una coincidencia dudosa y no se crea nada hasta que la revise una persona.
 * FALLIDA: un paso ha fallado (normalmente Odoo); reintentar continúa sin repetir lo ya hecho.
 */
public enum EstadoAltaSolicitud {
    NO_INICIADA, EN_PROCESO, REQUIERE_REVISION, FALLIDA, COMPLETADA
}
