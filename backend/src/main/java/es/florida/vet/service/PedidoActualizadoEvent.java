package es.florida.vet.service;

/** Solicitud de exportación/actualización tras confirmar pago, firma o logística. */
public record PedidoActualizadoEvent(Long pedidoId) { }
