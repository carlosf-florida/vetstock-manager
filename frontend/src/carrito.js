// Carrito en estado local (localStorage) hasta que se crea el pedido.
const CLAVE = 'vetstock_carrito'

export function leerCarrito() {
  try {
    const bruto = localStorage.getItem(CLAVE)
    const datos = bruto ? JSON.parse(bruto) : []
    return Array.isArray(datos) ? datos : []
  } catch {
    return []
  }
}

export function guardarCarrito(lineas) {
  localStorage.setItem(CLAVE, JSON.stringify(lineas))
  window.dispatchEvent(new Event('vetstock-carrito'))
}

export function vaciarCarrito() {
  localStorage.removeItem(CLAVE)
  window.dispatchEvent(new Event('vetstock-carrito'))
}

export function anadirAlCarrito(producto, cantidad = 1) {
  const lineas = leerCarrito()
  const existente = lineas.find((l) => l.productoId === producto.id)
  if (existente) {
    existente.cantidad += cantidad
  } else {
    lineas.push({
      productoId: producto.id,
      codigo: producto.codigo,
      nombre: producto.nombre,
      precio: producto.precio,
      cantidad,
    })
  }
  guardarCarrito(lineas)
}

export function unidadesCarrito() {
  return leerCarrito().reduce((suma, l) => suma + l.cantidad, 0)
}
