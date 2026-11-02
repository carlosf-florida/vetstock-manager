package es.florida.vet.exception;

/** Se lanza cuando no existe un producto, pedido, veterinario... (HTTP 404). */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
