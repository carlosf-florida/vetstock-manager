package es.florida.vet.enums;

/**
 * Estado de un intento de pago.
 * PENDIENTE  -> iniciado, esperando la notificación del proveedor
 * CONFIRMADO -> el proveedor lo confirmó (pedido pasa a LISTO_PREPARAR)
 * FALLADO    -> el pago no se realizó; se puede reintentar con una referencia nueva
 */
public enum EstadoPago {
    PENDIENTE,
    CONFIRMADO,
    FALLADO
}
