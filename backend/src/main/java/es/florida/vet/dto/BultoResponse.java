package es.florida.vet.dto;

import es.florida.vet.entity.Bulto;

import java.time.LocalDateTime;

public record BultoResponse(Long id, String identificador, Long pedidoId, Long paletId,
                            String clinicaNombre, LocalDateTime fechaAlta,
                            String paletIdentificador, String paletSalida, String salidaPedido) {

    public static BultoResponse desde(Bulto b) {
        return new BultoResponse(b.getId(), b.getIdentificador(), b.getPedido().getId(),
                b.getPalet() != null ? b.getPalet().getId() : null,
                b.getPedido().getClinica().getNombre(), b.getFechaAlta(),
                b.getPalet() != null ? b.getPalet().getIdentificador() : null,
                b.getPalet() != null ? b.getPalet().getDestino().getEtiqueta() : null,
                b.getPedido().getDestino() != null ? b.getPedido().getDestino().getEtiqueta() : null);
    }
}
