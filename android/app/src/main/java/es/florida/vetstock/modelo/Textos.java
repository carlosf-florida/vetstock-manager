package es.florida.vetstock.modelo;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import org.json.JSONObject;

/** Textos y reglas de presentación, las mismas que usa la web. El backend vuelve a comprobarlo todo. */
public final class Textos {
    private static final Locale ESPANA = Locale.forLanguageTag("es-ES");

    private Textos() {
    }

    public static String euros(String importe) {
        if (importe == null || importe.isEmpty() || "null".equals(importe)) {
            return "—";
        }
        return NumberFormat.getCurrencyInstance(ESPANA).format(new BigDecimal(importe));
    }

    public static String euros(BigDecimal importe) {
        return NumberFormat.getCurrencyInstance(ESPANA).format(importe);
    }

    public static String estadoPedido(String estado) {
        switch (estado) {
            case "BORRADOR": return "Borrador";
            case "PENDIENTE_FIRMA": return "Pendiente de firma";
            case "FIRMADO": return "Firmado · pendiente de pago";
            case "LISTO_PREPARAR": return "Listo para preparar";
            case "EN_PREPARACION": return "En preparación";
            case "PREPARADO": return "Preparado";
            case "EXPEDIDO": return "Expedido";
            case "CANCELADO": return "Cancelado";
            default: return estado;
        }
    }

    public static boolean firmaValida(JSONObject p) {
        JSONObject autorizacion = p.optJSONObject("autorizacion");
        return autorizacion != null && autorizacion.optBoolean("firmaValida");
    }

    public static String estadoFirma(JSONObject p) {
        if (firmaValida(p)) {
            return "Firma válida";
        }
        boolean tieneFirma = !p.isNull("fechaFirma");
        if ("CANCELADO".equals(p.optString("estado")) && !tieneFirma) {
            return "Sin firma";
        }
        return tieneFirma ? "Firma no válida" : "Pendiente de firma";
    }

    public static boolean pagado(JSONObject p) {
        return "PAGADO".equals(p.optString("estadoPago")) || !p.isNull("fechaPago");
    }

    public static String estadoPago(JSONObject p) {
        if (pagado(p)) {
            return "Pagado";
        }
        return "CANCELADO".equals(p.optString("estado")) ? "Sin pago" : "Pendiente";
    }

    private static boolean admiteFirma(String estado) {
        return "PENDIENTE_FIRMA".equals(estado) || "FIRMADO".equals(estado) || "LISTO_PREPARAR".equals(estado);
    }

    /** Solo el veterinario asignado, y solo si aún hace falta una firma válida. */
    public static boolean puedeFirmar(JSONObject p) {
        return Sesion.esVeterinario() && admiteFirma(p.optString("estado")) && !firmaValida(p)
                && Sesion.username() != null && Sesion.username().equals(p.optString("veterinarioUsername"));
    }

    public static boolean faltaPago(JSONObject p) {
        String estado = p.optString("estado");
        return ("PENDIENTE_FIRMA".equals(estado) || "FIRMADO".equals(estado)) && !pagado(p);
    }
}
