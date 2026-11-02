package es.florida.vet.exception;

/** Se lanza cuando un usuario intenta tocar algo que no es suyo (HTTP 403). */
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
