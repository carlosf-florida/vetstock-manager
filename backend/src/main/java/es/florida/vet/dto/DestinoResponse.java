package es.florida.vet.dto;

import es.florida.vet.entity.Destino;

public record DestinoResponse(Long id, String nombre) {
    public static DestinoResponse desde(Destino d) {
        return new DestinoResponse(d.getId(), d.getNombre());
    }
}
