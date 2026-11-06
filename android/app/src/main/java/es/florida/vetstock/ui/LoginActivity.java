package es.florida.vetstock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;

import es.florida.vetstock.R;
import es.florida.vetstock.api.ApiCliente;
import es.florida.vetstock.modelo.Sesion;

public class LoginActivity extends BaseActivity {
    public static final String EXTRA_AVISO = "aviso";

    private EditText usuario;
    private EditText contrasena;
    private Button entrar;

    @Override
    protected boolean requiereSesion() {
        return false;
    }

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        setContentView(R.layout.activity_login);
        usuario = findViewById(R.id.usuario);
        contrasena = findViewById(R.id.contrasena);
        entrar = findViewById(R.id.entrar);
        mostrarError(getIntent().getStringExtra(EXTRA_AVISO));
        entrar.setOnClickListener(v -> iniciar());
        contrasena.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_DONE) {
                iniciar();
                return true;
            }
            return false;
        });
    }

    private void iniciar() {
        if (!entrar.isEnabled()) {
            return;
        }
        String nombre = usuario.getText().toString().trim();
        String clave = contrasena.getText().toString();
        if (nombre.isEmpty() || clave.isEmpty()) {
            mostrarError(getString(R.string.datos_obligatorios));
            return;
        }
        if (!ApiCliente.configurado()) {
            mostrarError(getString(R.string.sin_url));
            return;
        }
        ocupado(true);
        mostrarError(null);
        enSegundoPlano(() -> ApiCliente.iniciarSesion(nombre, clave), datos -> {
            contrasena.setText("");
            Sesion.iniciar(datos);
            if (Sesion.esAdmin()) {
                // Sin panel de administración en la app
                new Thread(ApiCliente::cerrarSesion).start();
                Sesion.cerrar();
                ocupado(false);
                mostrarError(getString(R.string.admin_no_disponible));
                return;
            }
            Class<?> destino = Sesion.esVeterinario() ? PedidosActivity.class : CatalogoActivity.class;
            startActivity(new Intent(this, destino));
            finish();
        }, mensaje -> {
            ocupado(false);
            mostrarError(mensaje);
        });
    }

    private void ocupado(boolean si) {
        entrar.setEnabled(!si);
        mostrar(R.id.cargando, si);
    }
}
