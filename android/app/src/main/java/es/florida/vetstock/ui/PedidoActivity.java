package es.florida.vetstock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import es.florida.vetstock.R;
import es.florida.vetstock.api.ApiCliente;
import es.florida.vetstock.modelo.Textos;

/** Detalle y estado de un pedido. Todo lo que muestra viene del servidor. */
public class PedidoActivity extends BaseActivity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_AVISO = "aviso";

    private long id;
    private JSONObject pedido;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        setContentView(R.layout.activity_pedido);
        id = getIntent().getLongExtra(EXTRA_ID, -1);
        String aviso = getIntent().getStringExtra(EXTRA_AVISO);
        if (aviso != null) {
            mostrar(R.id.aviso, true);
            ponerTexto(R.id.aviso, aviso);
        }
        findViewById(R.id.enviar_firma).setOnClickListener(v -> enviarAFirma());
        findViewById(R.id.firmar).setOnClickListener(v -> startActivity(new Intent(this, FirmaActivity.class)
                .putExtra(FirmaActivity.EXTRA_ID, id)
                .putExtra(FirmaActivity.EXTRA_PEDIDO, pedido.toString())));
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargar();
    }

    private void cargar() {
        mostrar(R.id.cargando, true);
        mostrar(R.id.zona_error, false);
        enSegundoPlano(() -> ApiCliente.obtener("/api/pedidos/" + id), this::pintar, mensaje -> {
            mostrar(R.id.contenido, false);
            mostrarFalloCarga(mensaje, this::cargar);
        });
    }

    private void pintar(JSONObject p) {
        pedido = p;
        mostrar(R.id.cargando, false);
        mostrar(R.id.contenido, true);
        String estado = p.optString("estado");
        ponerTexto(R.id.numero, "Pedido #" + p.optLong("id"));
        ponerTexto(R.id.clinica, p.optString("clinicaNombre"));
        ponerTexto(R.id.estado, Textos.estadoPedido(estado));
        ponerTexto(R.id.firma, "Firma: " + Textos.estadoFirma(p));
        ponerTexto(R.id.pago, "Pago: " + Textos.estadoPago(p));
        ponerTexto(R.id.veterinario, "Veterinario asignado: " + p.optString("veterinarioNombre"));
        ponerTexto(R.id.base, "Base imponible: " + Textos.euros(p.optString("baseImponible")));
        ponerTexto(R.id.iva, "IVA (" + p.optString("ivaPorcentaje") + " %): " + Textos.euros(p.optString("importeIva")));
        ponerTexto(R.id.total, "Total: " + Textos.euros(p.optString("total")));
        ponerTexto(R.id.observaciones, p.isNull("observaciones") ? "" : "Observaciones: " + p.optString("observaciones"));
        pintarLineas(p.optJSONArray("lineas"));
        mostrar(R.id.nota_pago, Textos.faltaPago(p));
        mostrar(R.id.enviar_firma, "BORRADOR".equals(estado));
        mostrar(R.id.firmar, Textos.puedeFirmar(p));
    }

    private void pintarLineas(JSONArray lineas) {
        LinearLayout contenedor = findViewById(R.id.lineas);
        contenedor.removeAllViews();
        for (int i = 0; lineas != null && i < lineas.length(); i++) {
            JSONObject l = lineas.optJSONObject(i);
            TextView fila = new TextView(this);
            fila.setTextColor(ContextCompat.getColor(this, R.color.texto));
            fila.setPadding(0, 4, 0, 4);
            fila.setText(l.optInt("cantidad") + " × " + l.optString("nombreProducto") + " · " + Textos.euros(l.optString("importe")));
            contenedor.addView(fila);
        }
    }

    private void enviarAFirma() {
        Button boton = findViewById(R.id.enviar_firma);
        boton.setEnabled(false);
        mostrarError(null);
        enSegundoPlano(() -> ApiCliente.enviar("PATCH", "/api/pedidos/" + id + "/enviar-firma", null), datos -> {
            boton.setEnabled(true);
            mostrar(R.id.aviso, false);
            aviso(R.string.enviado_firma_ok);
            cargar();
        }, mensaje -> {
            boton.setEnabled(true);
            mostrarError(mensaje);
        });
    }
}
