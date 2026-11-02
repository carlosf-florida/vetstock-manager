package es.florida.vet.dto;

import es.florida.vet.entity.Palet;

import java.time.LocalDateTime;
import java.util.List;

public record PaletResponse(Long id, String identificador, Long destinoId, String destinoNombre,
                            LocalDateTime fechaExpedicion, List<BultoResponse> bultos) {

    public static PaletResponse desde(Palet p) {
        return new PaletResponse(p.getId(), p.getIdentificador(), p.getDestino().getId(),
                p.getDestino().getNombre(), p.getFechaExpedicion(),
                p.getBultos().stream().map(BultoResponse::desde).toList());
    }
}
