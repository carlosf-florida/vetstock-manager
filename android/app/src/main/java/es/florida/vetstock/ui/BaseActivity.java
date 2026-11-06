package es.florida.vetstock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import es.florida.vetstock.R;
import es.florida.vetstock.api.ApiCliente;
import es.florida.vetstock.api.ApiException;
import es.florida.vetstock.modelo.Sesion;

/** Lo común a las pantallas con sesión: hilos, menú, cierre de sesión y sesión caducada. */
public abstract class BaseActivity extends AppCompatActivity {

    /** Trabajo que se ejecuta fuera del hilo principal (Android no permite red en él). */
    public interface Trabajo<T> {
        T ejecutar() throws Exception;
    }

    private static final ExecutorService HILOS = Executors.newCachedThreadPool();
    private final Handler principal = new Handler(Looper.getMainLooper());

    /** Android puede cerrar el proceso: sin sesión en memoria se vuelve a iniciar sesión. */
    protected boolean requiereSesion() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle guardado) {
        super.onCreate(guardado);
        if (requiereSesion() && !Sesion.activa()) {
            irALogin(null);
        }
    }

    @Override
    public void setContentView(int layout) {
        super.setContentView(layout);
        // Desde Android 15 la app ocupa toda la pantalla: se deja hueco a las barras del sistema
        View raiz = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (vista, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            // El hueco superior que llega aquí ya incluye la barra de título
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });
    }

    protected <T> void enSegundoPlano(Trabajo<T> trabajo, Consumer<T> alTerminar, Consumer<String> alFallar) {
        HILOS.execute(() -> {
            try {
                T resultado = trabajo.ejecutar();
                principal.post(() -> {
                    if (!isDestroyed()) {
                        alTerminar.accept(resultado);
                    }
                });
            } catch (Exception e) {
                principal.post(() -> {
                    if (!isDestroyed()) {
                        tratarError(e, alFallar);
                    }
                });
            }
        });
    }

    private void tratarError(Exception e, Consumer<String> alFallar) {
        if (e instanceof ApiException) {
            if (((ApiException) e).getStatus() == 401 && requiereSesion()) {
                Sesion.cerrar();
                irALogin(getString(R.string.sesion_caducada));
                return;
            }
            alFallar.accept(e.getMessage());
        } else {
            alFallar.accept("Ha ocurrido un error inesperado.");
        }
    }

    protected void irALogin(@Nullable String aviso) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (aviso != null) {
            intent.putExtra(LoginActivity.EXTRA_AVISO, aviso);
        }
        startActivity(intent);
        finish();
    }

    protected void mostrar(int id, boolean visible) {
        View vista = findViewById(id);
        if (vista != null) {
            vista.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    protected void ponerTexto(int id, CharSequence texto) {
        ((TextView) findViewById(id)).setText(texto);
    }

    protected void mostrarError(@Nullable String texto) {
        TextView error = findViewById(R.id.error);
        error.setText(texto);
        error.setVisibility(texto == null ? View.GONE : View.VISIBLE);
    }

    /** Zona de error de pantalla completa, con botón para reintentar. */
    protected void mostrarFalloCarga(String texto, Runnable reintentar) {
        mostrar(R.id.cargando, false);
        mostrar(R.id.zona_error, true);
        ponerTexto(R.id.texto_error, texto);
        findViewById(R.id.boton_reintentar).setOnClickListener(v -> {
            mostrar(R.id.zona_error, false);
            reintentar.run();
        });
    }

    protected void aviso(int texto) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (requiereSesion()) {
            getMenuInflater().inflate(R.menu.menu_principal, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_catalogo) {
            startActivity(new Intent(this, CatalogoActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } else if (id == R.id.menu_carrito) {
            startActivity(new Intent(this, CarritoActivity.class));
        } else if (id == R.id.menu_pedidos) {
            startActivity(new Intent(this, PedidosActivity.class));
        } else if (id == R.id.menu_salir) {
            HILOS.execute(ApiCliente::cerrarSesion);
            Sesion.cerrar();
            irALogin(null);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
