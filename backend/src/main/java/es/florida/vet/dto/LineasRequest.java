package es.florida.vet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Cuerpo para reemplazar las líneas de un pedido en BORRADOR. */
public record LineasRequest(@NotEmpty @Valid List<LineaPedidoRequest> lineas) {
}
