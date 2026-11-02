package es.florida.vet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.florida.vet.entity.Firma;
import es.florida.vet.entity.LineaPedido;
import es.florida.vet.entity.Pedido;
import es.florida.vet.enums.EstadoPedido;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Situación de firma y pago de un pedido, calculada con reglas fijas a partir
 * de los datos guardados (no se almacena: así nunca se desincroniza).
 *
 * - Firma válida: existe, la hizo el VETERINARIO ASIGNADO al pedido y la copia
 *   firmada (líneas, cantidades, precios y total) coincide con el contenido actual.
 * - Pago completo: hay un pago CONFIRMADO por el proveedor por el total exacto.
 *   No hay pagos parciales: una notificación con otro importe deja el intento FALLADO.
 *
 * Solo un pedido con firma válida y pago completo puede prepararse.
 */
public record AutorizacionPedido(boolean firmaValida, String motivoFirma,
                                 boolean pagoCompleto, BigDecimal importePendiente,
                                 Clasificacion clasificacion, boolean listoParaPreparar) {

    /** Las tres listas que ve administración. */
    public enum Clasificacion { PAGADO_PENDIENTE_FIRMA, FIRMADO_Y_PAGADO, OTROS }

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * @param importeConfirmado importe del pago CONFIRMADO del pedido, o null si no hay ninguno
     */
    public static AutorizacionPedido de(Pedido pedido, BigDecimal importeConfirmado) {
        String motivo = motivoFirmaNoValida(pedido);
        boolean firmaValida = motivo == null;

        BigDecimal total = pedido.getTotal();
        boolean pagoCompleto = pedido.getFechaPago() != null
                && importeConfirmado != null && importeConfirmado.compareTo(total) == 0;
        BigDecimal pendiente = pagoCompleto ? BigDecimal.ZERO
                : total.subtract(importeConfirmado == null ? BigDecimal.ZERO : importeConfirmado).max(BigDecimal.ZERO);

        EstadoPedido estado = pedido.getEstado();
        boolean fueraDeCircuito = estado == EstadoPedido.BORRADOR || estado == EstadoPedido.CANCELADO;
        Clasificacion clasificacion;
        if (fueraDeCircuito || !pagoCompleto) {
            clasificacion = Clasificacion.OTROS;
        } else if (firmaValida) {
            clasificacion = Clasificacion.FIRMADO_Y_PAGADO;
        } else {
            clasificacion = Clasificacion.PAGADO_PENDIENTE_FIRMA;
        }
        boolean listo = clasificacion == Clasificacion.FIRMADO_Y_PAGADO && estado == EstadoPedido.LISTO_PREPARAR;
        return new AutorizacionPedido(firmaValida, motivo, pagoCompleto, pendiente, clasificacion, listo);
    }

    /** null si la firma es válida; si no, el motivo en texto para administración. */
    public static String motivoFirmaNoValida(Pedido pedido) {
        Firma firma = pedido.getFirma();
        String asignado = pedido.getVeterinario().getUsuario().getNombreCompleto();
        if (firma == null) {
            return "Falta la firma de " + asignado;
        }
        if (!firma.getUsuario().getId().equals(pedido.getVeterinario().getUsuario().getId())) {
            return "Firmado por " + firma.getUsuario().getNombreCompleto()
                    + ", que no es el veterinario asignado (" + asignado + ")";
        }
        if (!coincideConContenidoActual(firma.getContenidoPedido(), pedido)) {
            return "La firma corresponde a un contenido anterior del pedido";
        }
        return null;
    }

    /** Compara la copia firmada (JSON) con las líneas y el total actuales. */
    private static boolean coincideConContenidoActual(String copiaFirmada, Pedido pedido) {
        try {
            JsonNode copia = JSON.readTree(copiaFirmada);
            if (copia.path("total").isMissingNode()
                    || new BigDecimal(copia.path("total").asText()).compareTo(pedido.getTotal()) != 0) {
                return false;
            }
            List<String> firmadas = new ArrayList<>();
            for (JsonNode l : copia.path("lineas")) {
                firmadas.add(clave(l.path("codigo").asText(), l.path("cantidad").asInt(),
                        new BigDecimal(l.path("precioUnitario").asText())));
            }
            List<String> actuales = new ArrayList<>();
            for (LineaPedido l : pedido.getLineas()) {
                actuales.add(clave(l.getProducto().getCodigo(), l.getCantidad(), l.getPrecioUnitario()));
            }
            firmadas.sort(null);
            actuales.sort(null);
            return firmadas.equals(actuales);
        } catch (Exception e) {
            return false; // copia ilegible: no se acepta como autorización
        }
    }

    private static String clave(String codigo, int cantidad, BigDecimal precio) {
        return codigo + "|" + cantidad + "|" + precio.stripTrailingZeros().toPlainString();
    }
}
