package es.florida.vet.dto;

import es.florida.vet.entity.Bulto;

import java.time.LocalDateTime;

public record BultoResponse(Long id, String identificador, Long pedidoId, Long paletId,
                            String clinicaNombre, LocalDateTime fechaAlta) {

    public static BultoResponse desde(Bulto b) {
        return new BultoResponse(b.getId(), b.getIdentificador(), b.getPedido().getId(),
                b.getPalet() != null ? b.getPalet().getId() : null,
                b.getPedido().getClinica().getNombre(), b.getFechaAlta());
    }
}
