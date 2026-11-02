package es.florida.vet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Cuerpo para crear un pedido.
 * - ADMIN: veterinarioId y clinicaId obligatorios (pedido en nombre de un cliente).
 * - VETERINARIO: va a su clínica; si no envía veterinarioId, firmante = él mismo.
 * - AUXILIAR: va a su clínica y DEBE indicar veterinarioId (de su clínica).
 */
public record PedidoRequest(Long veterinarioId, Long clinicaId,
                            @jakarta.validation.constraints.Size(max = 255, message = "Las observaciones no pueden superar 255 caracteres") String observaciones,
                            @NotEmpty @Valid List<LineaPedidoRequest> lineas) {
}
