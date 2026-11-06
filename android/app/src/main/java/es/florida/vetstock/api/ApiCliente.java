package es.florida.vetstock.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import es.florida.vetstock.BuildConfig;

/**
 * Cliente HTTP de la app. La sesión es la misma que en la web: la cookie JSESSIONID que devuelve el
 * backend, guardada solo en memoria. Ni la contraseña ni la cookie se escriben en el dispositivo.
 * Sus métodos bloquean: se llaman siempre desde un hilo secundario (ver BaseActivity).
 */
public final class ApiCliente {

    private static final int TIEMPO_MS = 15000;
    private static final CookieManager COOKIES = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);

    static {
        CookieHandler.setDefault(COOKIES);
    }

    private ApiCliente() {
    }

    public static boolean configurado() {
        return !BuildConfig.API_URL.isEmpty();
    }

    /** El login del backend es un formulario, no JSON. Devuelve los datos del usuario. */
    public static JSONObject iniciarSesion(String usuario, String contrasena) throws ApiException {
        COOKIES.getCookieStore().removeAll();
        String cuerpo = "username=" + codificar(usuario) + "&password=" + codificar(contrasena);
        peticion("POST", "/api/auth/login", cuerpo, "application/x-www-form-urlencoded");
        return obtener("/api/auth/me");
    }

    /** Aunque el servidor no responda, en el dispositivo la sesión queda cerrada. */
    public static void cerrarSesion() {
        try {
            peticion("POST", "/api/auth/logout", null, null);
        } catch (ApiException ignorada) {
            // Se borra igualmente la cookie local
        } finally {
            COOKIES.getCookieStore().removeAll();
        }
    }

    public static JSONObject obtener(String ruta) throws ApiException {
        return aObjeto(peticion("GET", ruta, null, null));
    }

    public static JSONArray obtenerLista(String ruta) throws ApiException {
        try {
            return new JSONArray(peticion("GET", ruta, null, null));
        } catch (JSONException e) {
            throw new ApiException(0, "Respuesta no válida del servidor.");
        }
    }

    public static JSONObject enviar(String metodo, String ruta, JSONObject cuerpo) throws ApiException {
        return aObjeto(peticion(metodo, ruta, cuerpo == null ? null : cuerpo.toString(), "application/json"));
    }

    public static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private static JSONObject aObjeto(String texto) throws ApiException {
        try {
            return new JSONObject(texto);
        } catch (JSONException e) {
            throw new ApiException(0, "Respuesta no válida del servidor.");
        }
    }

    private static String peticion(String metodo, String ruta, String cuerpo, String tipo) throws ApiException {
        HttpURLConnection conexion = null;
        try {
            conexion = (HttpURLConnection) new URL(BuildConfig.API_URL + ruta).openConnection();
            conexion.setRequestMethod(metodo);
            conexion.setConnectTimeout(TIEMPO_MS);
            conexion.setReadTimeout(TIEMPO_MS);
            conexion.setRequestProperty("Accept", "application/json");
            if (cuerpo != null) {
                conexion.setDoOutput(true);
                conexion.setRequestProperty("Content-Type", tipo + "; charset=utf-8");
                try (OutputStream salida = conexion.getOutputStream()) {
                    salida.write(cuerpo.getBytes(StandardCharsets.UTF_8));
                }
            }
            int estado = conexion.getResponseCode();
            if (estado >= 200 && estado < 300) {
                return leer(conexion.getInputStream());
            }
            throw new ApiException(estado, mensajeDeError(estado, leer(conexion.getErrorStream())));
        } catch (IOException e) {
            throw new ApiException(0, "No se ha podido conectar con el servidor. Comprueba la conexión.");
        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    private static String mensajeDeError(int estado, String texto) {
        try {
            String mensaje = new JSONObject(texto).optString("mensaje");
            if (!mensaje.isEmpty()) {
                return mensaje;
            }
        } catch (JSONException ignorada) {
            // El cuerpo no es JSON: se usa el mensaje genérico
        }
        switch (estado) {
            case 401: return "No has iniciado sesión o la sesión ha caducado.";
            case 403: return "No tienes permiso para realizar esta operación.";
            case 404: return "El recurso solicitado no existe.";
            default:  return "Error " + estado + " del servidor.";
        }
    }

    private static String leer(InputStream entrada) throws IOException {
        if (entrada == null) {
            return "";
        }
        try (InputStream e = entrada) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] trozo = new byte[4096];
            int n;
            while ((n = e.read(trozo)) != -1) {
                bytes.write(trozo, 0, n);
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
}
