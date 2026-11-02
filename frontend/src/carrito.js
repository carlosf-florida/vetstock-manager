// Carrito guardado en el navegador (localStorage) hasta que se crea el pedido.
//
// Cada usuario tiene su propia clave ("vetstock_carrito_<usuario>"), así que si
// en el mismo navegador entra otra cuenta no ve el carrito de la anterior.
// Solo se guardan datos del catálogo (id, código, nombre, precio, stock y
// cantidad): nada de firmas, credenciales ni datos de pago.
// Precios, stock e importes definitivos los vuelve a comprobar el servidor.

import { dinero } from './format.js'

const PREFIJO = 'vetstock_carrito_'
const CLAVE_ANTIGUA = 'vetstock_carrito' // versión anterior: un único carrito para todo el navegador
export const EVENTO_CARRITO = 'vetstock-carrito'

let usuarioActual = null

/** Lo llama la sesión al entrar o salir: a partir de aquí se usa el carrito de ese usuario. */
export function usarCarritoDe(username) {
  usuarioActual = username || null
  try {
    // El carrito compartido antiguo no se puede atribuir a nadie: se descarta
    localStorage.removeItem(CLAVE_ANTIGUA)
  } catch { /* almacenamiento no disponible */ }
  avisarCambio()
}

function clave() {
  return usuarioActual ? PREFIJO + usuarioActual : null
}

function avisarCambio() {
  window.dispatchEvent(new Event(EVENTO_CARRITO))
}

export function leerCarrito() {
  const k = clave()
  if (!k) return []
  try {
    const datos = JSON.parse(localStorage.getItem(k) || '[]')
    return Array.isArray(datos) ? datos : []
  } catch {
    return []
  }
}

/** Guarda las líneas. Devuelve false si el navegador no permite guardar (modo privado, cuota llena...). */
function guardar(lineas) {
  const k = clave()
  if (!k) return false
  try {
    if (lineas.length === 0) localStorage.removeItem(k)
    else localStorage.setItem(k, JSON.stringify(lineas))
  } catch {
    return false
  }
  avisarCambio()
  return true
}

export function vaciarCarrito() {
  guardar([])
}

/** Unidades totales del carrito: es lo que muestra el contador de la cabecera. */
export function unidadesCarrito(lineas = leerCarrito()) {
  return lineas.reduce((suma, l) => suma + l.cantidad, 0)
}

/** Máximo que se puede pedir de una línea según el último stock conocido. */
export function maximoPermitido(linea) {
  const stock = Number(linea?.stockDisponible)
  return Number.isFinite(stock) ? Math.max(0, stock) : Infinity
}

/**
 * Añade unidades de un producto. Devuelve { ok, mensaje }: si algo falla, el
 * carrito NO cambia, así que el contador nunca sube por error.
 */
export function anadirAlCarrito(producto, cantidad = 1) {
  if (!usuarioActual) return { ok: false, mensaje: 'Inicia sesión para usar el carrito.' }
  if (!Number.isInteger(cantidad) || cantidad < 1) {
    return { ok: false, mensaje: 'La cantidad debe ser un número entero de 1 o más.' }
  }
  if (producto.activo === false) {
    return { ok: false, mensaje: `«${producto.nombre}» no está disponible.` }
  }
  const lineas = leerCarrito()
  const existente = lineas.find((l) => l.productoId === producto.id)
  const yaEnCarrito = existente ? existente.cantidad : 0
  const stock = Number(producto.stockDisponible)
  if (Number.isFinite(stock) && yaEnCarrito + cantidad > stock) {
    const libres = Math.max(0, stock - yaEnCarrito)
    return {
      ok: false,
      mensaje: libres === 0
        ? `Ya tienes en el carrito todas las unidades disponibles de «${producto.nombre}» (${stock}).`
        : `Solo puedes añadir ${libres} ${libres === 1 ? 'unidad' : 'unidades'} más de «${producto.nombre}»: hay ${stock} disponibles.`,
    }
  }
  const nueva = {
    productoId: producto.id,
    codigo: producto.codigo,
    nombre: producto.nombre,
    precio: producto.precio,
    stockDisponible: producto.stockDisponible,
    cantidad: yaEnCarrito + cantidad,
  }
  const actualizadas = existente
    ? lineas.map((l) => (l.productoId === producto.id ? nueva : l))
    : [...lineas, nueva]
  if (!guardar(actualizadas)) {
    return { ok: false, mensaje: 'No se ha podido guardar el carrito en este navegador. Revisa que no esté en modo privado o sin espacio.' }
  }
  return { ok: true }
}

/** Cambia la cantidad de una línea (entre 1 y el stock conocido). Devuelve true si se ha guardado. */
export function cambiarCantidad(productoId, cantidad) {
  const lineas = leerCarrito()
  const linea = lineas.find((l) => l.productoId === productoId)
  if (!linea || !Number.isInteger(cantidad) || cantidad < 1 || cantidad > maximoPermitido(linea)) return false
  return guardar(lineas.map((l) => (l.productoId === productoId ? { ...l, cantidad } : l)))
}

export function quitarDelCarrito(productoId) {
  return guardar(leerCarrito().filter((l) => l.productoId !== productoId))
}

/**
 * Actualiza nombre, precio y stock de las líneas con el catálogo actual y
 * devuelve los avisos para el usuario (producto retirado, menos stock, precio cambiado).
 */
export function sincronizarConCatalogo(productos) {
  const porId = new Map(productos.map((p) => [p.id, p]))
  const avisos = []
  const lineas = leerCarrito().map((l) => {
    const p = porId.get(l.productoId)
    if (!p || p.activo === false) {
      avisos.push({ productoId: l.productoId, tipo: 'retirado', texto: `«${l.nombre}» ya no está disponible: quítalo para continuar.` })
      return { ...l, stockDisponible: 0, noDisponible: true }
    }
    if (Number(p.precio) !== Number(l.precio)) {
      avisos.push({ productoId: l.productoId, tipo: 'precio', texto: `El precio de «${p.nombre}» ha cambiado a ${dinero(p.precio)}.` })
    }
    if (l.cantidad > Number(p.stockDisponible)) {
      avisos.push({ productoId: l.productoId, tipo: 'stock', texto: `De «${p.nombre}» solo quedan ${p.stockDisponible} disponibles: reduce la cantidad.` })
    }
    return { ...l, nombre: p.nombre, codigo: p.codigo, precio: p.precio, stockDisponible: p.stockDisponible, noDisponible: false }
  })
  guardar(lineas)
  return avisos
}
