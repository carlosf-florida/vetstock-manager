let usuario = null;
let pedido = null;
let pedidoId = null;

// Texto de confirmación y de éxito para cada acción
const ACCIONES = {
    autorizar: { confirmar: '¿Confirmas que revisaste el pedido y quieres autorizarlo?', exito: 'Pedido autorizado correctamente' },
    cancelar:  { confirmar: '¿Seguro que quieres cancelar el pedido? Se liberará el stock reservado.', exito: 'Pedido cancelado' },
    preparar:  { confirmar: '¿Marcar el pedido como preparado?', exito: 'Pedido marcado como preparado' },
    enviar:    { confirmar: '¿Marcar el pedido como enviado? El stock saldrá del almacén.', exito: 'Pedido marcado como enviado' }
};

document.addEventListener('DOMContentLoaded', async () => {
    usuario = await iniciarPagina();
    if (!usuario) return;

    const params = new URLSearchParams(window.location.search);
    pedidoId = Number(params.get('id'));
    if (!pedidoId) {
        mostrarMensaje('Falta el número de pedido', 'error');
        return;
    }
    if (params.get('creado') === '1') {
        mostrarMensaje('Pedido creado correctamente. Queda pendiente de autorización del veterinario.');
    }
    document.getElementById('acciones').addEventListener('click', (evento) => {
        const boton = evento.target.closest('button[data-accion]');
        if (boton) ejecutarAccion(boton.dataset.accion);
    });
    cargarPedido();
});

async function cargarPedido() {
    try {
        pedido = await api('/api/pedidos/' + pedidoId);
        pintarPedido();
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}

function pintarPedido() {
    document.getElementById('titulo').textContent = 'Pedido #' + pedido.id;

    document.getElementById('bloqueDatos').innerHTML = `<div class="detalle">
        <div><span>Estado</span>${etiquetaEstado(pedido.estado)}</div>
        <div><span>Clínica</span>${escapar(pedido.clinica)}</div>
        <div><span>Veterinario</span>${escapar(pedido.veterinario)}</div>
        <div><span>Creado por</span>${escapar(pedido.creadoPor)}</div>
        <div><span>Total de unidades</span>${pedido.totalUnidades}</div>
        <div><span>Observaciones</span>${escapar(pedido.observaciones) || '-'}</div>
    </div>`;

    document.getElementById('tablaLineas').innerHTML =
        `<table><thead><tr><th>Código</th><th>Producto</th><th class="num">Cantidad</th></tr></thead><tbody>` +
        pedido.lineas.map(l => `<tr><td>${escapar(l.codigo)}</td><td>${escapar(l.nombre)}</td><td class="num">${l.cantidad}</td></tr>`).join('') +
        '</tbody></table>';

    // Seguimiento: solo mostramos los pasos que ya han ocurrido
    const pasos = [`<li>📝 Creado el ${escapar(pedido.fechaCreacion)} por ${escapar(pedido.creadoPor)}</li>`];
    if (pedido.fechaAutorizacion) {
        pasos.push(`<li>✅ Autorizado digitalmente por <strong>${escapar(pedido.autorizadoPor)}</strong> el ${escapar(pedido.fechaAutorizacion)}</li>`);
    }
    if (pedido.fechaPreparacion) pasos.push(`<li>📦 Preparado el ${escapar(pedido.fechaPreparacion)}</li>`);
    if (pedido.fechaEnvio) pasos.push(`<li>🚚 Enviado el ${escapar(pedido.fechaEnvio)}</li>`);
    if (pedido.fechaCancelacion) pasos.push(`<li>❌ Cancelado el ${escapar(pedido.fechaCancelacion)}</li>`);
    document.getElementById('seguimiento').innerHTML = pasos.join('');

    pintarAcciones();
}

// Los botones que aparecen dependen del rol y del estado del pedido
function pintarAcciones() {
    const esAdmin = usuario.rol === 'ADMIN';
    const boton = (accion, texto, clase) =>
        `<button class="btn ${clase}" data-accion="${accion}">${texto}</button>`;
    const botones = [];

    if (!esAdmin && pedido.estado === 'PENDIENTE_AUTORIZACION') {
        botones.push(boton('autorizar', 'Autorizar pedido', 'btn-principal'));
        botones.push(boton('cancelar', 'Rechazar pedido', 'btn-peligro'));
    }
    if (esAdmin) {
        if (pedido.estado === 'AUTORIZADO') botones.push(boton('preparar', 'Marcar como preparado', 'btn-principal'));
        if (pedido.estado === 'PREPARADO') botones.push(boton('enviar', 'Marcar como enviado', 'btn-principal'));
        if (pedido.estado === 'PENDIENTE_AUTORIZACION' || pedido.estado === 'AUTORIZADO') {
            botones.push(boton('cancelar', 'Cancelar pedido', 'btn-peligro'));
        }
    }
    document.getElementById('acciones').innerHTML = botones.join('');
    document.getElementById('bloqueAcciones').classList.toggle('oculto', botones.length === 0);
}

async function ejecutarAccion(accion) {
    const textos = ACCIONES[accion];
    if (!textos || !confirm(textos.confirmar)) return;
    try {
        pedido = await api(`/api/pedidos/${pedidoId}/${accion}`, { method: 'PATCH' });
        mostrarMensaje(textos.exito);
        pintarPedido();
    } catch (e) {
        mostrarMensaje(e.message, 'error');
        cargarPedido(); // por si el estado cambió mientras tanto
    }
}
