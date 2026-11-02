package es.florida.vet.enums;

/**
 * Estado logístico del pedido. Firma y pago pueden llegar en cualquier orden:
 * PENDIENTE_FIRMA puede estar ya pagado; FIRMADO significa "firmado, pendiente de pago".
 * LISTO_PREPARAR exige firma válida del veterinario asignado y pago completo
 * (se vuelve a comprobar al preparar, ver AutorizacionPedido).
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
