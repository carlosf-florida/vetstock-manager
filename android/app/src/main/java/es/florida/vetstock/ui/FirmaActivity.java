package es.florida.vetstock.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import es.florida.vetstock.R;
import es.florida.vetstock.api.ApiCliente;
import es.florida.vetstock.modelo.Textos;

/**
 * Firma del veterinario. Muestra lo que se autoriza y envía el dibujo al servidor, que comprueba que
 * es el veterinario asignado y guarda la firma ligada al contenido actual del pedido.
 */
public class FirmaActivity extends BaseActivity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_PEDIDO = "pedido";

    private long id;
    private FirmaView lienzo;
    private CheckBox confirmo;
    private Button firmar;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        setContentView(R.layout.activity_firma);
        id = getIntent().getLongExtra(EXTRA_ID, -1);
        lienzo = findViewById(R.id.lienzo);
        confirmo = findViewById(R.id.confirmo);
        firmar = findViewById(R.id.firmar);
        pintarResumen(getIntent().getStringExtra(EXTRA_PEDIDO));
        findViewById(R.id.borrar).setOnClickListener(v -> lienzo.borrar());
        firmar.setOnClickListener(v -> enviar());
    }

    private void pintarResumen(String texto) {
        try {
            JSONObject p = new JSONObject(texto);
            ponerTexto(R.id.resumen, "Pedido #" + id + " · " + Textos.euros(p.optString("total")));
            StringBuilder resumen = new StringBuilder();
            JSONArray lineas = p.getJSONArray("lineas");
            for (int i = 0; i < lineas.length(); i++) {
                JSONObject l = lineas.getJSONObject(i);
                resumen.append(l.optInt("cantidad")).append(" × ").append(l.optString("nombreProducto")).append('\n');
            }
            ponerTexto(R.id.lineas, resumen.toString().trim());
        } catch (JSONException e) {
            finish();
        }
    }

    private void enviar() {
        if (!lienzo.tieneFirma() || !confirmo.isChecked()) {
            mostrarError(getString(R.string.firma_incompleta));
            return;
        }
        ocupado(true);
        mostrarError(null);
        String imagen = lienzo.aDataUrlPng();
        enSegundoPlano(() -> {
            JSONObject cuerpo = new JSONObject().put("tipoFirma", "DIBUJO").put("imagenBase64", imagen).put("confirmacion", true);
            return ApiCliente.enviar("PATCH", "/api/pedidos/" + id + "/firmar", cuerpo);
        }, datos -> {
            aviso(R.string.firmado_ok);
            finish();
        }, mensaje -> {
            ocupado(false);
            mostrarError(mensaje);
        });
    }

    private void ocupado(boolean si) {
        firmar.setEnabled(!si);
        mostrar(R.id.cargando, si);
    }
}
