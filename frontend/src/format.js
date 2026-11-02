// Formato y catálogos de estados (siempre color + texto + icono, nunca solo color)

export function dinero(importe) {
  const n = Number(importe ?? 0)
  return (Number.isFinite(n) ? n : 0).toFixed(2) + ' €'
}

// Estimación visual para pedidos nuevos; el importe definitivo siempre lo calcula el servidor.
export function estimarImportesPedido(lineas) {
  const ivaPorcentaje = 4
  let baseImponibleCents = 0
  let importeIvaCents = 0

  for (const linea of Array.isArray(lineas) ? lineas : []) {
    const precioCents = Math.round(Number(linea?.precio ?? linea?.precioUnitario ?? 0) * 100)
    const cantidad = Number(linea?.cantidad ?? 0)
    if (!Number.isFinite(precioCents) || !Number.isInteger(cantidad) || cantidad < 1) continue

    const baseLineaCents = precioCents * cantidad
    const ivaLineaCents = Math.floor((baseLineaCents * ivaPorcentaje + 50) / 100)
    baseImponibleCents += baseLineaCents
    importeIvaCents += ivaLineaCents
  }

  return {
    baseImponible: baseImponibleCents / 100,
    ivaPorcentaje,
    importeIva: importeIvaCents / 100,
    moneda: 'EUR',
    total: (baseImponibleCents + importeIvaCents) / 100,
  }
}

export function fecha(valor) {
  if (!valor) return '—'
  const d = new Date(valor)
  if (Number.isNaN(d.getTime())) return String(valor)
  return d.toLocaleString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function fechaCorta(valor) {
  if (!valor) return '—'
  const d = new Date(valor)
  if (Number.isNaN(d.getTime())) return String(valor)
  return d.toLocaleDateString('es-ES')
}

export const ESTADOS_PEDIDO = {
  BORRADOR: { texto: 'Borrador', icono: '📝', clase: 'est-borrador' },
  PENDIENTE_FIRMA: { texto: 'Pendiente de firma', icono: '✍️', clase: 'est-pendiente-firma' },
  FIRMADO: { texto: 'Firmado', icono: '🖋️', clase: 'est-firmado' },
  LISTO_PREPARAR: { texto: 'Listo para preparar', icono: '✅', clase: 'est-listo' },
  EN_PREPARACION: { texto: 'En preparación', icono: '🛠️', clase: 'est-preparacion' },
  PREPARADO: { texto: 'Preparado', icono: '📦', clase: 'est-preparado' },
  EXPEDIDO: { texto: 'Expedido', icono: '🚚', clase: 'est-expedido' },
  CANCELADO: { texto: 'Cancelado', icono: '❌', clase: 'est-cancelado' },
}

export const ESTADOS_PAGO = {
  PENDIENTE: { texto: 'Pendiente de pago', icono: '⏳', clase: 'est-pago-pendiente' },
  CONFIRMADO: { texto: 'Pago confirmado', icono: '💳', clase: 'est-pago-confirmado' },
  FALLADO: { texto: 'Pago fallido', icono: '⚠️', clase: 'est-pago-fallado' },
}

export function estadoPedido(estado) {
  return ESTADOS_PEDIDO[estado] || { texto: estado || 'Desconocido', icono: '❔', clase: 'est-otro' }
}

export function estadoPago(estado) {
  return ESTADOS_PAGO[estado] || { texto: estado || 'Sin información de pago', icono: '❔', clase: 'est-otro' }
}

export const NOMBRE_ESTADO_PEDIDO = (e) => estadoPedido(e).texto

export const ROLES = { ADMIN: 'Administración', VETERINARIO: 'Veterinario', AUXILIAR: 'Auxiliar' }

export function totalPedido(pedido) {
  return pedido?.total ?? pedido?.importe ?? pedido?.importeTotal ?? null
}
