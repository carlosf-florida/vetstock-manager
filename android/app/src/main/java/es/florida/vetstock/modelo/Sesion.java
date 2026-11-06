package es.florida.vetstock.modelo;

import org.json.JSONObject;

/** Usuario de la sesión actual, en memoria: si Android cierra la app, se vuelve a iniciar sesión. */
public final class Sesion {
    private static String username;
    private static String rol;

    private Sesion() {
    }

    public static void iniciar(JSONObject usuario) {
        username = usuario.optString("username");
        rol = usuario.optString("rol");
    }

    public static void cerrar() {
        username = null;
        rol = null;
        Carrito.vaciar();
    }

    public static boolean activa() {
        return username != null;
    }

    public static String username() {
        return username;
    }

    public static boolean esVeterinario() {
        return "VETERINARIO".equals(rol);
    }

    public static boolean esAdmin() {
        return "ADMIN".equals(rol);
    }
}
