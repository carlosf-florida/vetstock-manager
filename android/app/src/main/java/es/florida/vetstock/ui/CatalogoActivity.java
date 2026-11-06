package es.florida.vetstock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import es.florida.vetstock.R;
import es.florida.vetstock.api.ApiCliente;
import es.florida.vetstock.modelo.Textos;

public class CatalogoActivity extends BaseActivity {
    private final List<JSONObject> productos = new ArrayList<>();
    private final Adaptador adaptador = new Adaptador();
    private EditText busqueda;
    private int ultimaPeticion;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        setContentView(R.layout.activity_catalogo);
        busqueda = findViewById(R.id.busqueda);
        RecyclerView lista = findViewById(R.id.lista);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adaptador);
        findViewById(R.id.boton_buscar).setOnClickListener(v -> cargar());
        busqueda.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_SEARCH) {
                cargar();
                return true;
            }
            return false;
        });
        cargar();
    }

    private void cargar() {
        // Si llegan varias respuestas, solo cuenta la de la última búsqueda
        int peticion = ++ultimaPeticion;
        String texto = busqueda.getText().toString().trim();
        mostrar(R.id.cargando, true);
        mostrar(R.id.zona_error, false);
        mostrar(R.id.vacio, false);
        enSegundoPlano(() -> ApiCliente.obtenerLista("/api/productos?busqueda=" + ApiCliente.codificar(texto)), datos -> {
            if (peticion == ultimaPeticion) {
                pintar(datos);
            }
        }, mensaje -> {
            if (peticion == ultimaPeticion) {
                productos.clear();
                adaptador.notifyDataSetChanged();
                mostrarFalloCarga(mensaje, this::cargar);
            }
        });
    }

    private void pintar(JSONArray datos) {
        productos.clear();
        for (int i = 0; i < datos.length(); i++) {
            JSONObject producto = datos.optJSONObject(i);
            if (producto != null && producto.optBoolean("activo")) {
                productos.add(producto);
            }
        }
        adaptador.notifyDataSetChanged();
        mostrar(R.id.cargando, false);
        mostrar(R.id.vacio, productos.isEmpty());
    }

    private class Adaptador extends RecyclerView.Adapter<Adaptador.Fila> {
        class Fila extends RecyclerView.ViewHolder {
            final TextView nombre, detalle, precio, stock;

            Fila(View vista) {
                super(vista);
                nombre = vista.findViewById(R.id.nombre);
                detalle = vista.findViewById(R.id.detalle);
                precio = vista.findViewById(R.id.precio);
                stock = vista.findViewById(R.id.stock);
            }
        }

        @NonNull
        @Override
        public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
            return new Fila(LayoutInflater.from(padre.getContext()).inflate(R.layout.item_producto, padre, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Fila fila, int posicion) {
            JSONObject p = productos.get(posicion);
            fila.nombre.setText(p.optString("nombre"));
            fila.detalle.setText(p.optString("codigo") + " · " + p.optString("categoria"));
            fila.precio.setText(Textos.euros(p.optString("precio")));
            fila.stock.setText(getString(R.string.disponible, p.optInt("stockDisponible")));
            fila.itemView.setOnClickListener(v -> startActivity(new Intent(CatalogoActivity.this, ProductoActivity.class)
                    .putExtra(ProductoActivity.EXTRA_ID, p.optLong("id"))));
        }

        @Override
        public int getItemCount() {
            return productos.size();
        }
    }
}
