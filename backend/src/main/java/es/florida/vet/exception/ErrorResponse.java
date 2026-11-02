package es.florida.vet.exception;

/** Formato JSON de todos los errores de la API. */
public record ErrorResponse(int estado, String mensaje, String fecha) {
}
