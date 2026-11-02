package es.florida.vet.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Pasa las fechas a texto legible (dd/MM/yyyy HH:mm) para enviarlas al frontend. */
public final class FechaUtil {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FechaUtil() {
    }

    public static String formatear(LocalDateTime fecha) {
        return fecha == null ? null : fecha.format(FORMATO);
    }
}
