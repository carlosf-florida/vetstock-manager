package es.florida.vetstock.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import org.json.JSONObject;

import java.math.BigDecimal;

import es.florida.vetstock.R;
import es.florida.vetstock.api.ApiCliente;
import es.florida.vetstock.modelo.Carrito;
import es.florida.vetstock.modelo.Textos;

public class ProductoActivity extends BaseActivity {
    public static final String EXTRA_ID = "id";

    private long id;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        setContentView(R.layout.activity_producto);
        id = getIntent().getLongExtra(EXTRA_ID, -1);
        cargar();
    }

    private void cargar() {
        mostrar(R.id.cargando, true);
        enSegundoPlano(() -> ApiCliente.obtener("/api/productos/" + id), this::pintar,
                mensaje -> mostrarFalloCarga(mensaje, this::cargar));
    }

    private void pintar(JSONObject p) {
        mostrar(R.id.cargando, false);
        mostrar(R.id.contenido, true);
        int disponible = p.optInt("stockDisponible");
        ponerTexto(R.id.nombre, p.optString("nombre"));
        ponerTexto(R.id.codigo, p.optString("codigo") + " · " + p.optString("categoria"));
        ponerTexto(R.id.descripcion, p.isNull("descripcion") ? "" : p.optString("descripcion"));
        ponerTexto(R.id.precio, Textos.euros(p.optString("precio")));
        ponerTexto(R.id.stock, disponible > 0 ? getString(R.string.disponible, disponible) : getString(R.string.sin_stock));
        Button anadir = findViewById(R.id.anadir);
        anadir.setEnabled(disponible > 0);
        anadir.setOnClickListener(v -> anadirAlCarrito(p, disponible));
    }

    private void anadirAlCarrito(JSONObject p, int disponible) {
        int maximo = Math.min(disponible, Carrito.MAXIMO_POR_LINEA);
        int cantidad;
        try {
            cantidad = Integer.parseInt(((EditText) findViewById(R.id.cantidad)).getText().toString());
        } catch (NumberFormatException e) {
            cantidad = 0;
        }
        if (cantidad < 1 || cantidad > maximo) {
            mostrarError(getString(R.string.cantidad_no_valida, maximo));
            return;
        }
        mostrarError(null);
        Carrito.anadir(id, p.optString("nombre"), new BigDecimal(p.optString("precio")), disponible, cantidad);
        aviso(R.string.anadido_carrito);
        finish();
    }
}
