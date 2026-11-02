package es.florida.vet.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import es.florida.vet.util.FechaUtil;

/**
 * Recoge las excepciones de toda la aplicación y las convierte en
 * respuestas JSON con el código HTTP adecuado.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException ex) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> reglaNegocio(ReglaNegocioException ex) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccesoDenegadoException ex) {
        return construir(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // Falla alguna anotación de validación (@NotBlank, @Min, @NotEmpty...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(". "));
        return construir(HttpStatus.BAD_REQUEST, mensaje);
    }

    // JSON mal formado o con un valor no válido (por ejemplo, una categoría que no existe)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> jsonNoValido(HttpMessageNotReadableException ex) {
        return construir(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos. Revisa el formulario.");
    }

    // Parámetro de la URL con tipo incorrecto (por ejemplo, estado=INVENTADO)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> parametroNoValido(MethodArgumentTypeMismatchException ex) {
        return construir(HttpStatus.BAD_REQUEST, "El valor del parámetro '" + ex.getName() + "' no es válido");
    }

    // Última defensa: restricciones de la base de datos (duplicados, etc.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException ex) {
        return construir(HttpStatus.BAD_REQUEST, "No se pudo guardar: hay datos repetidos o no válidos");
    }

    // Dos usuarios cambiaron el mismo registro a la vez (@Version): el segundo debe recargar
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> concurrencia(ObjectOptimisticLockingFailureException ex) {
        return construir(HttpStatus.CONFLICT, "Otra persona ha modificado estos datos a la vez. Recarga e inténtalo de nuevo.");
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus estado, String mensaje) {
        ErrorResponse cuerpo = new ErrorResponse(estado.value(), mensaje, FechaUtil.formatear(LocalDateTime.now()));
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
