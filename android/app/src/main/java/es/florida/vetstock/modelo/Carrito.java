package es.florida.vetstock.modelo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Carrito local. Solo envía producto y cantidad: el precio mostrado es orientativo y el pedido lo
 * calcula el servidor, que también vuelve a comprobar el stock.
 */
public final class Carrito {
    public static final int MAXIMO_POR_LINEA = 9999;

    public static final class Linea {
        public final long productoId;
        public final String nombre;
        public final BigDecimal precio;
        public final int stockDisponible;
        public int cantidad;

        Linea(long productoId, String nombre, BigDecimal precio, int stockDisponible, int cantidad) {
            this.productoId = productoId;
            this.nombre = nombre;
            this.precio = precio;
            this.stockDisponible = stockDisponible;
            this.cantidad = cantidad;
        }

        public int maximo() {
            return Math.min(stockDisponible, MAXIMO_POR_LINEA);
        }

        public BigDecimal importe() {
            return precio.multiply(BigDecimal.valueOf(cantidad));
        }
    }

    private static final Map<Long, Linea> LINEAS = new LinkedHashMap<>();

    private Carrito() {
    }

    /** Suma al carrito sin superar el máximo. Devuelve la cantidad que queda en la línea. */
    public static int anadir(long id, String nombre, BigDecimal precio, int stockDisponible, int cantidad) {
        Linea existente = LINEAS.get(id);
        int anterior = existente == null ? 0 : existente.cantidad;
        Linea nueva = new Linea(id, nombre, precio, stockDisponible, anterior);
        nueva.cantidad = Math.min(anterior + cantidad, nueva.maximo());
        LINEAS.put(id, nueva);
        return nueva.cantidad;
    }

    public static List<Linea> lineas() {
        return new ArrayList<>(LINEAS.values());
    }

    public static void quitar(long id) {
        LINEAS.remove(id);
    }

    public static void vaciar() {
        LINEAS.clear();
    }

    public static boolean estaVacio() {
        return LINEAS.isEmpty();
    }

    public static BigDecimal subtotal() {
        BigDecimal suma = BigDecimal.ZERO;
        for (Linea l : LINEAS.values()) {
            suma = suma.add(l.importe());
        }
        return suma;
    }

    public static JSONObject aPedido(String observaciones) throws JSONException {
        JSONArray lineas = new JSONArray();
        for (Linea l : LINEAS.values()) {
            lineas.put(new JSONObject().put("productoId", l.productoId).put("cantidad", l.cantidad));
        }
        JSONObject pedido = new JSONObject().put("lineas", lineas);
        if (!observaciones.isEmpty()) {
            pedido.put("observaciones", observaciones);
        }
        return pedido;
    }
}
