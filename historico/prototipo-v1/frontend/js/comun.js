// Funciones comunes a todas las páginas

const NOMBRES_ESTADO = {
    PENDIENTE_AUTORIZACION: 'Pendiente de autorización',
    AUTORIZADO: 'Autorizado',
    PREPARADO: 'Preparado',
    ENVIADO: 'Enviado',
    CANCELADO: 'Cancelado'
};

const NOMBRES_CATEGORIA = {
    MEDICAMENTO: 'Medicamento',
    MATERIAL_CLINICO: 'Material clínico',
    HIGIENE: 'Higiene',
    ALIMENTACION: 'Alimentación'
};

// Evita que un texto con < o > se interprete como HTML (seguridad básica)
function escapar(valor) {
    if (valor === null || valor === undefined) return '';
    return String(valor)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function etiquetaEstado(estado) {
    return `<span class="estado estado-${escapar(estado)}">${escapar(NOMBRES_ESTADO[estado] || estado)}</span>`;
}

// Llama a la API. Si hay error lanza una excepción con el mensaje del servidor.
// opciones: method, body (objeto JSON), form (datos de formulario), sinRedirigir
async function api(url, opciones = {}) {
    const config = { method: opciones.method || 'GET', headers: {}, credentials: 'same-origin' };
    if (opciones.body) {
        config.headers['Content-Type'] = 'application/json';
        config.body = JSON.stringify(opciones.body);
    }
    if (opciones.form) {
        config.headers['Content-Type'] = 'application/x-www-form-urlencoded';
        config.body = new URLSearchParams(opciones.form).toString();
    }

    let respuesta;
    try {
        respuesta = await fetch(url, config);
    } catch (e) {
        throw new Error('No se pudo conectar con el servidor');
    }

    let datos = null;
    const texto = await respuesta.text();
    if (texto) {
        try { datos = JSON.parse(texto); } catch (e) { datos = null; }
    }

    if (!respuesta.ok) {
        // 401 = sin sesión: volvemos a la pantalla de login
        if (respuesta.status === 401 && !opciones.sinRedirigir) {
            window.location.href = 'index.html';
        }
        throw new Error((datos && datos.mensaje) || 'Error inesperado (' + respuesta.status + ')');
    }
    return datos;
}

// Mensaje emergente de éxito o error (se cierra solo o al hacer clic)
function mostrarMensaje(texto, tipo = 'exito') {
    const contenedor = document.getElementById('mensajes');
    if (!contenedor) { alert(texto); return; }
    const div = document.createElement('div');
    div.className = 'mensaje mensaje-' + tipo;
    div.textContent = texto;
    div.addEventListener('click', () => div.remove());
    contenedor.appendChild(div);
    setTimeout(() => div.remove(), tipo === 'error' ? 8000 : 4500);
}

// Espera un poco antes de ejecutar (para no llamar a la API en cada tecla)
function retrasar(funcion, milisegundos = 300) {
    let temporizador;
    return () => {
        clearTimeout(temporizador);
        temporizador = setTimeout(funcion, milisegundos);
    };
}

// Comprueba la sesión y pinta la cabecera. Devuelve el usuario o null si no debe seguir.
async function iniciarPagina(opciones = {}) {
    let usuario;
    try {
        usuario = await api('/api/auth/me');
    } catch (e) {
        return null; // api() ya redirige al login si no hay sesión
    }
    if (opciones.soloAdmin && usuario.rol !== 'ADMIN') {
        window.location.href = 'dashboard.html';
        return null;
    }
    pintarCabecera(usuario);
    return usuario;
}

function pintarCabecera(usuario) {
    const enlaces = usuario.rol === 'ADMIN'
        ? [['dashboard.html', 'Inicio'], ['productos.html', 'Productos'], ['stock.html', 'Stock'],
           ['veterinarios.html', 'Veterinarios'], ['pedidos.html', 'Pedidos']]
        : [['dashboard.html', 'Inicio'], ['pedidos.html', 'Mis pedidos']];
    const paginaActual = window.location.pathname.split('/').pop();
    const menu = enlaces.map(([ruta, texto]) =>
        `<a href="${ruta}" class="${ruta === paginaActual ? 'activo' : ''}">${texto}</a>`).join('');
    const rol = usuario.rol === 'ADMIN' ? 'Almacén' : (usuario.clinica || 'Veterinario');

    document.getElementById('cabecera').innerHTML = `
        <div class="cabecera-interior">
            <a class="logo" href="dashboard.html">🐾 VetStock Manager</a>
            <button class="btn-menu" id="btnMenu" aria-label="Abrir menú">☰</button>
            <nav id="menu">${menu}</nav>
            <div class="usuario">
                <span>${escapar(usuario.nombreCompleto)} · ${escapar(rol)}</span>
                <button class="btn btn-secundario" id="btnSalir">Salir</button>
            </div>
        </div>`;
    document.getElementById('btnMenu').addEventListener('click', () =>
        document.getElementById('menu').classList.toggle('abierto'));
    document.getElementById('btnSalir').addEventListener('click', cerrarSesion);
}

async function cerrarSesion() {
    try { await api('/api/auth/logout', { method: 'POST', sinRedirigir: true }); } catch (e) { /* da igual */ }
    window.location.href = 'index.html';
}
