document.addEventListener('DOMContentLoaded', async () => {
    const usuario = await iniciarPagina({ soloAdmin: true });
    if (!usuario) return;

    document.getElementById('formMovimiento').addEventListener('submit', registrarMovimiento);
    document.getElementById('filtroProducto').addEventListener('change', cargarMovimientos);

    await cargarProductos();
    cargarMovimientos();
});

async function cargarProductos() {
    try {
        const productos = await api('/api/productos');
        const opciones = productos.map(p =>
            `<option value="${p.id}">${escapar(p.codigo)} - ${escapar(p.nombre)} (actual: ${p.stockActual}, disponible: ${p.stockDisponible})</option>`).join('');
        const seleccionado = document.getElementById('producto').value;
        const filtro = document.getElementById('filtroProducto').value;
        document.getElementById('producto').innerHTML = opciones;
        document.getElementById('filtroProducto').innerHTML =
            '<option value="">Todos los productos</option>' +
            productos.map(p => `<option value="${p.id}">${escapar(p.codigo)} - ${escapar(p.nombre)}</option>`).join('');
        if (seleccionado) document.getElementById('producto').value = seleccionado;
        if (filtro) document.getElementById('filtroProducto').value = filtro;
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}

async function cargarMovimientos() {
    const productoId = document.getElementById('filtroProducto').value;
    const url = '/api/stock/movimientos' + (productoId ? '?productoId=' + productoId : '');
    try {
        const movimientos = await api(url);
        const contenedor = document.getElementById('tablaMovimientos');
        if (movimientos.length === 0) {
            contenedor.innerHTML = '<div class="vacio">No hay movimientos</div>';
            return;
        }
        contenedor.innerHTML = `<table><thead><tr><th>Fecha</th><th>Producto</th><th>Tipo</th>
                <th class="num">Cantidad</th><th>Motivo</th><th>Usuario</th><th>Pedido</th></tr></thead><tbody>` +
            movimientos.map(m => `<tr>
                <td>${escapar(m.fecha)}</td>
                <td>${escapar(m.productoCodigo)} - ${escapar(m.productoNombre)}</td>
                <td><span class="etiqueta etiqueta-${escapar(m.tipo)}">${m.tipo === 'ENTRADA' ? 'Entrada' : 'Salida'}</span></td>
                <td class="num">${m.cantidad}</td>
                <td>${escapar(m.motivo)}</td>
                <td>${escapar(m.usuario)}</td>
                <td>${m.pedidoId ? '<a href="pedido-detalle.html?id=' + m.pedidoId + '">#' + m.pedidoId + '</a>' : '-'}</td>
            </tr>`).join('') + '</tbody></table>';
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}

async function registrarMovimiento(evento) {
    evento.preventDefault();
    const tipo = document.querySelector('input[name="tipo"]:checked').value; // entrada o salida
    const datos = {
        productoId: Number(document.getElementById('producto').value),
        cantidad: Number(document.getElementById('cantidad').value),
        motivo: document.getElementById('motivo').value.trim()
    };
    try {
        await api('/api/stock/' + tipo, { method: 'POST', body: datos });
        mostrarMensaje(tipo === 'entrada' ? 'Entrada registrada correctamente' : 'Salida registrada correctamente');
        document.getElementById('cantidad').value = '';
        document.getElementById('motivo').value = '';
        await cargarProductos(); // para refrescar el stock que se muestra
        cargarMovimientos();
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}
