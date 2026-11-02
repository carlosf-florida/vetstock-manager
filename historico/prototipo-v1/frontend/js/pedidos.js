document.addEventListener('DOMContentLoaded', async () => {
    const usuario = await iniciarPagina();
    if (!usuario) return;
    const esAdmin = usuario.rol === 'ADMIN';

    document.getElementById('titulo').textContent = esAdmin ? 'Pedidos' : 'Mis pedidos';
    if (esAdmin) {
        document.querySelectorAll('.solo-admin').forEach(e => e.classList.remove('oculto'));
        await cargarVeterinarios();
    }

    // Si venimos del dashboard con ?estado=XXX, dejamos el filtro puesto
    const estadoUrl = new URLSearchParams(window.location.search).get('estado');
    if (estadoUrl) document.getElementById('filtroEstado').value = estadoUrl;

    document.getElementById('filtroEstado').addEventListener('change', cargarPedidos);
    document.getElementById('filtroVeterinario').addEventListener('change', cargarPedidos);
    cargarPedidos();
});

async function cargarVeterinarios() {
    try {
        const lista = await api('/api/veterinarios');
        document.getElementById('filtroVeterinario').innerHTML =
            '<option value="">Todos</option>' +
            lista.map(v => `<option value="${v.id}">${escapar(v.clinica)}</option>`).join('');
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}

async function cargarPedidos() {
    const params = new URLSearchParams();
    const estado = document.getElementById('filtroEstado').value;
    const veterinario = document.getElementById('filtroVeterinario').value;
    if (estado) params.set('estado', estado);
    if (veterinario) params.set('veterinarioId', veterinario);

    try {
        const pedidos = await api('/api/pedidos?' + params.toString());
        const contenedor = document.getElementById('tablaPedidos');
        if (pedidos.length === 0) {
            contenedor.innerHTML = '<div class="vacio">No hay pedidos con esos filtros</div>';
            return;
        }
        contenedor.innerHTML = `<table><thead><tr><th>Nº</th><th>Clínica</th><th>Veterinario</th><th>Estado</th>
                <th>Fecha</th><th class="num">Unidades</th><th></th></tr></thead><tbody>` +
            pedidos.map(p => `<tr>
                <td>#${p.id}</td><td>${escapar(p.clinica)}</td><td>${escapar(p.veterinario)}</td>
                <td>${etiquetaEstado(p.estado)}</td><td>${escapar(p.fechaCreacion)}</td>
                <td class="num">${p.totalUnidades}</td>
                <td><a class="btn btn-pequeno" href="pedido-detalle.html?id=${p.id}">Ver</a></td>
            </tr>`).join('') + '</tbody></table>';
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}
