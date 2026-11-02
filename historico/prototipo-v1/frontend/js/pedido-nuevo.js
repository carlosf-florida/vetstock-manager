let productos = [];  // productos activos con stock disponible
let lineas = [];     // líneas añadidas: { productoId, codigo, nombre, cantidad }

document.addEventListener('DOMContentLoaded', async () => {
    const usuario = await iniciarPagina({ soloAdmin: true });
    if (!usuario) return;

    document.getElementById('btnAnadir').addEventListener('click', anadirLinea);
    document.getElementById('btnCrear').addEventListener('click', crearPedido);
    document.getElementById('tablaLineas').addEventListener('click', (evento) => {
        const boton = evento.target.closest('button[data-quitar]');
        if (!boton) return;
        lineas = lineas.filter(l => l.productoId !== Number(boton.dataset.quitar));
        pintarLineas();
    });

    await cargarDatos();
    pintarLineas();
});

async function cargarDatos() {
    try {
        const veterinarios = await api('/api/veterinarios');
        document.getElementById('veterinario').innerHTML =
            '<option value="">-- Elige un veterinario --</option>' +
            veterinarios.map(v => `<option value="${v.id}">${escapar(v.clinica)} (${escapar(v.nombreCompleto)})</option>`).join('');

        const todos = await api('/api/productos');
        productos = todos.filter(p => p.activo && p.stockDisponible > 0);
        document.getElementById('producto').innerHTML = productos.map(p =>
            `<option value="${p.id}">${escapar(p.codigo)} - ${escapar(p.nombre)} (disponible: ${p.stockDisponible})</option>`).join('');
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}

function anadirLinea() {
    const productoId = Number(document.getElementById('producto').value);
    const cantidad = Number(document.getElementById('cantidad').value);
    const producto = productos.find(p => p.id === productoId);

    // Comprobaciones rápidas en el navegador (el servidor vuelve a validar todo)
    if (!producto) { mostrarMensaje('Elige un producto', 'error'); return; }
    if (!Number.isInteger(cantidad) || cantidad < 1) { mostrarMensaje('La cantidad debe ser mayor que 0', 'error'); return; }
    if (lineas.some(l => l.productoId === productoId)) { mostrarMensaje('Ese producto ya está en el pedido', 'error'); return; }
    if (cantidad > producto.stockDisponible) {
        mostrarMensaje(`Stock insuficiente de ${producto.nombre}: disponible ${producto.stockDisponible}`, 'error');
        return;
    }
    lineas.push({ productoId: producto.id, codigo: producto.codigo, nombre: producto.nombre, cantidad: cantidad });
    document.getElementById('cantidad').value = 1;
    pintarLineas();
}

function pintarLineas() {
    const contenedor = document.getElementById('tablaLineas');
    if (lineas.length === 0) {
        contenedor.innerHTML = '<div class="vacio">Todavía no has añadido ningún producto</div>';
        return;
    }
    const total = lineas.reduce((suma, l) => suma + l.cantidad, 0);
    contenedor.innerHTML = `<table><thead><tr><th>Código</th><th>Producto</th><th class="num">Cantidad</th><th></th></tr></thead><tbody>` +
        lineas.map(l => `<tr><td>${escapar(l.codigo)}</td><td>${escapar(l.nombre)}</td><td class="num">${l.cantidad}</td>
            <td><button class="btn btn-pequeno btn-peligro" data-quitar="${l.productoId}">Quitar</button></td></tr>`).join('') +
        `</tbody><tfoot><tr><th colspan="2">Total unidades</th><th class="num">${total}</th><th></th></tr></tfoot></table>`;
}

async function crearPedido() {
    const veterinarioId = Number(document.getElementById('veterinario').value);
    if (!veterinarioId) { mostrarMensaje('Debes elegir un veterinario', 'error'); return; }
    if (lineas.length === 0) { mostrarMensaje('El pedido debe tener al menos un producto', 'error'); return; }

    const datos = {
        veterinarioId: veterinarioId,
        observaciones: document.getElementById('observaciones').value.trim(),
        lineas: lineas.map(l => ({ productoId: l.productoId, cantidad: l.cantidad }))
    };
    try {
        const pedido = await api('/api/pedidos', { method: 'POST', body: datos });
        window.location.href = 'pedido-detalle.html?id=' + pedido.id + '&creado=1';
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}
