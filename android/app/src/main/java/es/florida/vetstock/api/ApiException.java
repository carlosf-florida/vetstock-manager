package es.florida.vetstock.api;

/** Error de la API. status = 0 significa que no se ha podido conectar con el servidor. */
public class ApiException extends Exception {
    private final int status;

    public ApiException(int status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
