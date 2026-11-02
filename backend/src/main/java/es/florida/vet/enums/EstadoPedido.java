package es.florida.vet.enums;

/**
 * Estado operativo del pedido. Orden obligatorio: firma del veterinario
 * asignado -> pago confirmado por el proveedor -> preparación.
 * FIRMADO significa "firmado, pendiente de pago".
 * LISTO_PREPARAR exige firma registrada y pago confirmado.
 */
public enum EstadoPedido {
    BORRADOR, PENDIENTE_FIRMA, FIRMADO, LISTO_PREPARAR,
    EN_PREPARACION, PREPARADO, EXPEDIDO, CANCELADO;

    public boolean puedePasarA(EstadoPedido destino) {
        return switch (this) {
            case BORRADOR -> destino == PENDIENTE_FIRMA || destino == CANCELADO;
            case PENDIENTE_FIRMA -> destino == FIRMADO || destino == CANCELADO;
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
