package es.florida.vet.dto;

import es.florida.vet.entity.Documento;

import java.time.LocalDateTime;

/** Metadatos de un documento adjunto. El contenido se descarga por su endpoint propio. */
public record DocumentoResponse(Long id, Long pedidoId, String tipo, String nombreArchivo,
                                LocalDateTime fecha, String subidoPor) {

    public static DocumentoResponse desde(Documento d) {
        return new DocumentoResponse(d.getId(), d.getPedido().getId(), d.getTipo().name(),
                d.getNombreArchivo(), d.getFecha(), d.getSubidoPor().getNombreCompleto());
    }
}
