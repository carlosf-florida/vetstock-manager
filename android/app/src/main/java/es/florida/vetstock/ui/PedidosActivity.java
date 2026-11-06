package es.florida.vetstock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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
import es.florida.vetstock.modelo.Sesion;
import es.florida.vetstock.modelo.Textos;

/** Pedidos de la clínica. El veterinario ve primero los que esperan su firma. */
public class PedidosActivity extends BaseActivity {
    private final List<JSONObject> todos = new ArrayList<>();
    private final List<JSONObject> visibles = new ArrayList<>();
    private final Adaptador adaptador = new Adaptador();
    private CheckBox soloFirma;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        setContentView(R.layout.activity_pedidos);
        RecyclerView lista = findViewById(R.id.lista);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adaptador);
        soloFirma = findViewById(R.id.solo_firma);
        if (Sesion.esVeterinario()) {
            soloFirma.setVisibility(View.VISIBLE);
            soloFirma.setChecked(true);
        }
        soloFirma.setOnCheckedChangeListener((boton, marcado) -> filtrar());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Al volver de firmar, la lista debe reflejar el estado real del servidor
        if (Sesion.activa()) {
            cargar();
        }
    }

    private void cargar() {
        mostrar(R.id.cargando, true);
        mostrar(R.id.zona_error, false);
        mostrar(R.id.vacio, false);
        enSegundoPlano(() -> ApiCliente.obtenerLista("/api/pedidos"), this::pintar, mensaje -> {
            todos.clear();
            visibles.clear();
            adaptador.notifyDataSetChanged();
            mostrarFalloCarga(mensaje, this::cargar);
        });
    }

    private void pintar(JSONArray datos) {
        todos.clear();
        for (int i = 0; i < datos.length(); i++) {
            todos.add(datos.optJSONObject(i));
        }
        mostrar(R.id.cargando, false);
        filtrar();
    }

    private void filtrar() {
        visibles.clear();
        for (JSONObject p : todos) {
            if (!soloFirma.isChecked() || Textos.puedeFirmar(p)) {
                visibles.add(p);
            }
        }
        adaptador.notifyDataSetChanged();
        mostrar(R.id.vacio, visibles.isEmpty());
    }

    private class Adaptador extends RecyclerView.Adapter<Adaptador.Fila> {
        class Fila extends RecyclerView.ViewHolder {
            final TextView numero, total, clinica, estado, autorizacion;

            Fila(View vista) {
                super(vista);
                numero = vista.findViewById(R.id.numero);
                total = vista.findViewById(R.id.total);
                clinica = vista.findViewById(R.id.clinica);
                estado = vista.findViewById(R.id.estado);
                autorizacion = vista.findViewById(R.id.autorizacion);
            }
        }

        @NonNull
        @Override
        public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
            return new Fila(LayoutInflater.from(padre.getContext()).inflate(R.layout.item_pedido, padre, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Fila fila, int posicion) {
            JSONObject p = visibles.get(posicion);
            fila.numero.setText("Pedido #" + p.optLong("id"));
            fila.total.setText(Textos.euros(p.optString("total")));
            fila.clinica.setText(p.optString("clinicaNombre"));
            fila.estado.setText(Textos.estadoPedido(p.optString("estado")));
            fila.autorizacion.setText("Firma: " + Textos.estadoFirma(p) + " · Pago: " + Textos.estadoPago(p));
            fila.itemView.setOnClickListener(v -> startActivity(new Intent(PedidosActivity.this, PedidoActivity.class)
                    .putExtra(PedidoActivity.EXTRA_ID, p.optLong("id"))));
        }

        @Override
        public int getItemCount() {
            return visibles.size();
        }
    }
}
