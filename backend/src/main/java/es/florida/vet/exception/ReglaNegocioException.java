package es.florida.vet.exception;

/** Se lanza cuando se incumple una regla del negocio o una validación (HTTP 400). */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
