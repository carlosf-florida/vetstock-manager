function tarjetaResumen(texto, numero, enlace, aviso) {
    return `<a class="dato ${aviso ? 'aviso' : ''}" href="${enlace}">
                <div class="numero">${numero}</div>
                <div class="texto">${texto}</div>
            </a>`;
}

document.addEventListener('DOMContentLoaded', async () => {
    const usuario = await iniciarPagina();
    if (!usuario) return;
    const esAdmin = usuario.rol === 'ADMIN';
    document.getElementById('saludo').textContent = 'Hola, ' + usuario.nombreCompleto;

    try {
        const d = await api('/api/dashboard');

        document.getElementById('tarjetas').innerHTML =
            tarjetaResumen(esAdmin ? 'Pedidos pendientes de autorización' : 'Pedidos pendientes de tu autorización',
                d.pedidosPendientes, 'pedidos.html?estado=PENDIENTE_AUTORIZACION', d.pedidosPendientes > 0) +
            tarjetaResumen('Autorizados (por preparar)', d.pedidosAutorizados, 'pedidos.html?estado=AUTORIZADO', false) +
            tarjetaResumen('Preparados (por enviar)', d.pedidosPreparados, 'pedidos.html?estado=PREPARADO', false) +
            tarjetaResumen('Enviados', d.pedidosEnviados, 'pedidos.html?estado=ENVIADO', false);

        // Stock bajo: solo lo ve el almacén
        if (esAdmin) {
            document.getElementById('bloqueStockBajo').classList.remove('oculto');
            document.getElementById('tablaStockBajo').innerHTML = d.stockBajo.length === 0
                ? '<div class="vacio">No hay productos con stock bajo</div>'
                : `<table><thead><tr><th>Código</th><th>Producto</th><th class="num">Disponible</th><th class="num">Mínimo</th></tr></thead><tbody>` +
                  d.stockBajo.map(p => `<tr class="fila-bajo">
                      <td>${escapar(p.codigo)}</td><td>${escapar(p.nombre)}</td>
                      <td class="num">${p.stockDisponible}</td><td class="num">${p.stockMinimo}</td></tr>`).join('') +
                  '</tbody></table>';
        }

        document.getElementById('tablaUltimos').innerHTML = d.ultimosPedidos.length === 0
            ? '<div class="vacio">Todavía no hay pedidos</div>'
            : `<table><thead><tr><th>Nº</th><th>Clínica</th><th>Estado</th><th>Fecha</th><th class="num">Unidades</th><th></th></tr></thead><tbody>` +
              d.ultimosPedidos.map(p => `<tr>
                  <td>#${p.id}</td><td>${escapar(p.clinica)}</td><td>${etiquetaEstado(p.estado)}</td>
                  <td>${escapar(p.fechaCreacion)}</td><td class="num">${p.totalUnidades}</td>
                  <td><a class="btn btn-pequeno" href="pedido-detalle.html?id=${p.id}">Ver</a></td></tr>`).join('') +
              '</tbody></table>';
    } catch (e) {
        mostrarMensaje(e.message, 'error');
    }
});
