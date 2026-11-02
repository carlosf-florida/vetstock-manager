let productos = [];
let editandoId = null; // null = creando un producto nuevo

document.addEventListener('DOMContentLoaded', async () => {
    const usuario = await iniciarPagina({ soloAdmin: true });
    if (!usuario) return;

    document.getElementById('btnNuevo').addEventListener('click', () => abrirFormulario(null));
    document.getElementById('btnCancelar').addEventListener('click', cerrarFormulario);
    document.getElementById('formProducto').addEventListener('submit', guardarProducto);
    document.getElementById('filtroBuscar').addEventListener('input', retrasar(cargarProductos));
    document.getElementById('filtroCategoria').addEventListener('change', cargarProductos);
    document.getElementById('filtroBajo').addEventListener('change', cargarProductos);
    document.getElementById('tablaProductos').addEventListener('click', clicEnTabla);

    cargarProductos();
});

async function cargarProductos() {
    const params = new URLSearchParams();
    const buscar = document.getElementById('filtroBuscar').value.trim();
    const categoria = document.getElementById('filtroCategoria').value;
    if (buscar) params.set('buscar', buscar);
    if (categoria) params.set('categoria', categoria);
    if (document.getElementById('filtroBajo').checked) params.set('soloStockBajo', 'true');

    try {
        productos = await api('/api/productos?' + params.toString());
        pintarTabla();
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}

function pintarTabla() {
    const contenedor = document.getElementById('tablaProductos');
    if (productos.length === 0) {
        contenedor.innerHTML = '<div class="vacio">No se encontraron productos</div>';
        return;
    }
    contenedor.innerHTML = `<table><thead><tr>
            <th>Código</th><th>Nombre</th><th>Categoría</th><th class="num">Actual</th><th class="num">Reservado</th>
            <th class="num">Disponible</th><th class="num">Mínimo</th><th>Estado</th><th>Acciones</th>
        </tr></thead><tbody>` +
        productos.map(p => `<tr class="${p.stockBajo ? 'fila-bajo' : ''}">
            <td>${escapar(p.codigo)}</td>
            <td>${escapar(p.nombre)}</td>
            <td>${escapar(NOMBRES_CATEGORIA[p.categoria] || p.categoria)}</td>
            <td class="num">${p.stockActual}</td>
            <td class="num">${p.stockReservado}</td>
            <td class="num"><strong>${p.stockDisponible}</strong> ${p.stockBajo ? '<span class="etiqueta etiqueta-bajo">Bajo</span>' : ''}</td>
            <td class="num">${p.stockMinimo}</td>
            <td><span class="etiqueta ${p.activo ? 'etiqueta-ok' : 'etiqueta-no'}">${p.activo ? 'Activo' : 'Inactivo'}</span></td>
            <td><div class="acciones">
                <button class="btn btn-pequeno" data-accion="editar" data-id="${p.id}">Editar</button>
                <button class="btn btn-pequeno ${p.activo ? 'btn-peligro' : ''}" data-accion="${p.activo ? 'desactivar' : 'activar'}" data-id="${p.id}">${p.activo ? 'Desactivar' : 'Activar'}</button>
            </div></td>
        </tr>`).join('') + '</tbody></table>';
}

async function clicEnTabla(evento) {
    const boton = evento.target.closest('button[data-accion]');
    if (!boton) return;
    const id = Number(boton.dataset.id);
    const accion = boton.dataset.accion;

    if (accion === 'editar') {
        abrirFormulario(productos.find(p => p.id === id));
        return;
    }
    // activar / desactivar
    try {
        await api(`/api/productos/${id}/${accion}`, { method: 'PATCH' });
        mostrarMensaje(accion === 'activar' ? 'Producto activado' : 'Producto desactivado');
        cargarProductos();
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}

function abrirFormulario(producto) {
    editandoId = producto ? producto.id : null;
    document.getElementById('tituloFormulario').textContent = producto ? 'Editar producto' : 'Nuevo producto';
    document.getElementById('codigo').value = producto ? producto.codigo : '';
    document.getElementById('nombre').value = producto ? producto.nombre : '';
    document.getElementById('descripcion').value = producto && producto.descripcion ? producto.descripcion : '';
    document.getElementById('categoria').value = producto ? producto.categoria : 'MEDICAMENTO';
    document.getElementById('stockMinimo').value = producto ? producto.stockMinimo : 0;
    document.getElementById('stockInicial').value = 0;
    // El stock inicial solo tiene sentido al crear
    document.getElementById('campoStockInicial').classList.toggle('oculto', producto !== null && producto !== undefined);
    document.getElementById('bloqueFormulario').classList.remove('oculto');
    document.getElementById('codigo').focus();
}

function cerrarFormulario() {
    document.getElementById('bloqueFormulario').classList.add('oculto');
    editandoId = null;
}

async function guardarProducto(evento) {
    evento.preventDefault();
    const datos = {
        codigo: document.getElementById('codigo').value.trim(),
        nombre: document.getElementById('nombre').value.trim(),
        descripcion: document.getElementById('descripcion').value.trim(),
        categoria: document.getElementById('categoria').value,
        stockMinimo: Number(document.getElementById('stockMinimo').value),
        stockInicial: Number(document.getElementById('stockInicial').value)
    };
    try {
        if (editandoId === null) {
            await api('/api/productos', { method: 'POST', body: datos });
            mostrarMensaje('Producto creado correctamente');
        } else {
            await api('/api/productos/' + editandoId, { method: 'PUT', body: datos });
            mostrarMensaje('Producto actualizado correctamente');
        }
        cerrarFormulario();
        cargarProductos();
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
}
