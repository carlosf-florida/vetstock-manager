package es.florida.vetstock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

import es.florida.vetstock.R;
import es.florida.vetstock.api.ApiCliente;
import es.florida.vetstock.api.ApiException;
import es.florida.vetstock.modelo.Carrito;
import es.florida.vetstock.modelo.Textos;

public class CarritoActivity extends BaseActivity {
    private final Adaptador adaptador = new Adaptador();
    private List<Carrito.Linea> lineas;
    private boolean enviando;

    /** Resultado de crear el pedido: si falla el envío a firma, el pedido ya existe como borrador. */
    private static final class Creado {
        final long id;
        final String errorEnvio;

        Creado(long id, String errorEnvio) {
            this.id = id;
            this.errorEnvio = errorEnvio;
        }
    }

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        setContentView(R.layout.activity_carrito);
        RecyclerView lista = findViewById(R.id.lista);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adaptador);
        findViewById(R.id.guardar).setOnClickListener(v -> crearPedido(false));
        findViewById(R.id.enviar).setOnClickListener(v -> crearPedido(true));
        refrescar();
    }

    private void refrescar() {
        lineas = Carrito.lineas();
        adaptador.notifyDataSetChanged();
        boolean vacio = lineas.isEmpty();
        mostrar(R.id.vacio, vacio);
        mostrar(R.id.resumen, !vacio);
        ponerTexto(R.id.subtotal, getString(R.string.subtotal_sin_iva, Textos.euros(Carrito.subtotal())));
    }

    private void crearPedido(boolean enviarAFirma) {
        // Evita crear dos pedidos con una doble pulsación
        if (enviando) {
            return;
        }
        ocupado(true);
        mostrarError(null);
        String observaciones = ((EditText) findViewById(R.id.observaciones)).getText().toString().trim();
        enSegundoPlano(() -> {
            JSONObject pedido = ApiCliente.enviar("POST", "/api/pedidos", Carrito.aPedido(observaciones));
            long id = pedido.getLong("id");
            if (!enviarAFirma) {
                return new Creado(id, null);
            }
            try {
                ApiCliente.enviar("PATCH", "/api/pedidos/" + id + "/enviar-firma", null);
                return new Creado(id, null);
            } catch (ApiException e) {
                if (e.getStatus() == 401) {
                    throw e;
                }
                return new Creado(id, e.getMessage());
            }
        }, creado -> {
            Carrito.vaciar();
            Intent intent = new Intent(this, PedidoActivity.class).putExtra(PedidoActivity.EXTRA_ID, creado.id);
            if (creado.errorEnvio != null) {
                intent.putExtra(PedidoActivity.EXTRA_AVISO, getString(R.string.borrador_error, creado.errorEnvio));
            }
            startActivity(intent);
            finish();
        }, mensaje -> {
            ocupado(false);
            mostrarError(mensaje);
        });
    }

    private void ocupado(boolean si) {
        enviando = si;
        ((Button) findViewById(R.id.guardar)).setEnabled(!si);
        ((Button) findViewById(R.id.enviar)).setEnabled(!si);
        mostrar(R.id.cargando, si);
    }

    private class Adaptador extends RecyclerView.Adapter<Adaptador.Fila> {
        class Fila extends RecyclerView.ViewHolder {
            final TextView nombre, importe, cantidad;
            final Button restar, sumar, eliminar;

            Fila(View vista) {
                super(vista);
                nombre = vista.findViewById(R.id.nombre);
                importe = vista.findViewById(R.id.importe);
                cantidad = vista.findViewById(R.id.cantidad);
                restar = vista.findViewById(R.id.restar);
                sumar = vista.findViewById(R.id.sumar);
                eliminar = vista.findViewById(R.id.eliminar);
            }
        }

        @NonNull
        @Override
        public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
            return new Fila(LayoutInflater.from(padre.getContext()).inflate(R.layout.item_carrito, padre, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Fila fila, int posicion) {
            Carrito.Linea l = lineas.get(posicion);
            fila.nombre.setText(l.nombre);
            fila.importe.setText(Textos.euros(l.precio) + " × " + l.cantidad + " = " + Textos.euros(l.importe()));
            fila.cantidad.setText(String.valueOf(l.cantidad));
            fila.restar.setEnabled(l.cantidad > 1);
            fila.sumar.setEnabled(l.cantidad < l.maximo());
            fila.restar.setOnClickListener(v -> cambiar(l, l.cantidad - 1));
            fila.sumar.setOnClickListener(v -> cambiar(l, l.cantidad + 1));
            fila.eliminar.setOnClickListener(v -> {
                Carrito.quitar(l.productoId);
                refrescar();
            });
        }

        private void cambiar(Carrito.Linea linea, int cantidad) {
            linea.cantidad = cantidad;
            refrescar();
        }

        @Override
        public int getItemCount() {
            return lineas == null ? 0 : lineas.size();
        }
    }
}
