package es.florida.vet.service;

import es.florida.vet.exception.ReglaNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Límite sencillo de envíos por dirección IP para los formularios públicos, en memoria. Se reinicia
 * con el servidor y detrás de un proxy todos comparten IP: basta para este proyecto, no para producción.
 */
@Component
public class LimitadorEnvios {

    private static final Duration VENTANA = Duration.ofHours(1);
    private final Map<String, Deque<Instant>> envios = new HashMap<>();

    @Value("${vetstock.solicitudes.max-por-hora:20}")
    private int maximo = 20;

    public synchronized void comprobar(String ip) {
        Instant ahora = Instant.now();
        envios.values().forEach(lista -> lista.removeIf(t -> t.isBefore(ahora.minus(VENTANA))));
        envios.values().removeIf(Deque::isEmpty);
        Deque<Instant> lista = envios.computeIfAbsent(ip, k -> new ArrayDeque<>());
        if (lista.size() >= maximo) {
            throw new ReglaNegocioException("Se han recibido demasiados envíos. Inténtalo de nuevo más tarde.");
        }
        lista.add(ahora);
    }
}
