document.addEventListener('DOMContentLoaded', async () => {
    const usuario = await iniciarPagina({ soloAdmin: true });
    if (!usuario) return;

    document.getElementById('btnNuevo').addEventListener('click', () =>
        document.getElementById('bloqueFormulario').classList.remove('oculto'));
    document.getElementById('btnCancelar').addEventListener('click', cerrarFormulario);
    document.getElementById('formVeterinario').addEventListener('submit', guardarVeterinario);
    document.getElementById('filtroBuscar').addEventListener('input', retrasar(cargarVeterinarios));

    cargarVeterinarios();
});

async function cargarVeterinarios() {
    const buscar = document.getElementById('filtroBuscar').value.trim();
    const url = '/api/veterinarios' + (buscar ? '?buscar=' + encodeURIComponent(buscar) : '');
    try {
        const lista = await api(url);
        const contenedor = document.getElementById('tablaVeterinarios');
        if (lista.length === 0) {
            contenedor.innerHTML = '<div class="vacio">No se encontraron veterinarios</div>';
            return;
        }
        contenedor.innerHTML = `<table><thead><tr><th>Nombre</th><th>Clínica</th><th>Colegiado</th>
                <th>Teléfono</th><th>Dirección</th><th>Usuario</th></tr></thead><tbody>` +
            lista.map(v => `<tr>
                <td>${escapar(v.nombreCompleto)}</td><td>${escapar(v.clinica)}</td>
                <td>${escapar(v.numeroColegiado)}</td><td>${escapar(v.telefono)}</td>
                <td>${escapar(v.direccion)}</td><td>${escapar(v.username)}</td></tr>`).join('') +
            '</tbody></table>';
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}

function cerrarFormulario() {
    document.getElementById('formVeterinario').reset();
    document.getElementById('bloqueFormulario').classList.add('oculto');
}

async function guardarVeterinario(evento) {
    evento.preventDefault();
    const datos = {
        nombreCompleto: document.getElementById('nombreCompleto').value.trim(),
        clinica: document.getElementById('clinica').value.trim(),
        numeroColegiado: document.getElementById('numeroColegiado').value.trim(),
        telefono: document.getElementById('telefono').value.trim(),
        direccion: document.getElementById('direccion').value.trim(),
        username: document.getElementById('username').value.trim(),
        password: document.getElementById('password').value
    };
    try {
        await api('/api/veterinarios', { method: 'POST', body: datos });
        mostrarMensaje('Veterinario creado correctamente');
        cerrarFormulario();
        cargarVeterinarios();
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}
