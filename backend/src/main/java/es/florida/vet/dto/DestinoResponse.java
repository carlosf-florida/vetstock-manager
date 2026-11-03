package es.florida.vet.dto;

import es.florida.vet.entity.Destino;

public record DestinoResponse(Long id, String codigo, String nombre, String etiqueta) {
    public static DestinoResponse desde(Destino d) {
        return d == null ? null : new DestinoResponse(d.getId(), d.getCodigo(), d.getNombre(), d.getEtiqueta());
    }
}
