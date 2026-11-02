package es.florida.vet.enums;

/**
 * Estado operativo del pedido. Firma y pago se exponen por separado.
 * PENDIENTE_FIRMA puede estar ya pagado y exportado a Odoo.
 * LISTO_PREPARAR exige pago confirmado y una confirmación interna registrada.
 */
public enum EstadoPedido {
    BORRADOR, PENDIENTE_FIRMA, FIRMADO, LISTO_PREPARAR,
    EN_PREPARACION, PREPARADO, EXPEDIDO, CANCELADO;

    public boolean puedePasarA(EstadoPedido destino) {
        return switch (this) {
            case BORRADOR -> destino == PENDIENTE_FIRMA || destino == CANCELADO;
            case PENDIENTE_FIRMA -> destino == FIRMADO || destino == LISTO_PREPARAR || destino == CANCELADO;
            case FIRMADO -> destino == LISTO_PREPARAR || destino == CANCELADO;
            case LISTO_PREPARAR -> destino == EN_PREPARACION;
            case EN_PREPARACION -> destino == PREPARADO;
            case PREPARADO -> destino == EXPEDIDO;
            case EXPEDIDO, CANCELADO -> false;
        };
    }

    public boolean tieneReserva() {
        return switch (this) {
            case PENDIENTE_FIRMA, FIRMADO, LISTO_PREPARAR, EN_PREPARACION, PREPARADO -> true;
            default -> false;
        };
    }
}
