// Estado de autorización y pago separado del estado logístico del pedido.
// Mantiene compatibilidad con las respuestas anteriores mientras llega el contrato nuevo.

const ROLES_FIRMANTE = {
  VETERINARIO: 'Veterinario',
  AUXILIAR: 'Auxiliar',
}

export function estadoFirmaPedido(pedido) {
  if (pedido?.estadoFirma) return pedido.estadoFirma
  return pedido?.fechaFirma ? 'FIRMADO' : 'PENDIENTE'
}

export function estadoPagoPedido(pedido) {
  const estado = pedido?.estadoPago || pedido?.pago?.estado
  if (estado === 'PAGADO' || estado === 'CONFIRMADO' || pedido?.fechaPago) return 'PAGADO'
  if (estado === 'FALLADO') return 'FALLADO'
  return 'PENDIENTE'
}

export function infoEstadoFirma(pedido) {
  return estadoFirmaPedido(pedido) === 'FIRMADO'
    ? { texto: 'Firmada', clase: 'est-firmado' }
    : { texto: 'Pendiente', clase: 'est-pendiente-firma' }
}

export function infoEstadoPago(pedido) {
  const estado = estadoPagoPedido(pedido)
  if (estado === 'PAGADO') return { texto: 'Pagado', clase: 'est-pago-confirmado' }
  if (estado === 'FALLADO') return { texto: 'Pago fallido', clase: 'est-pago-fallado' }
  return { texto: 'Pendiente', clase: 'est-pago-pendiente' }
}

export function nombreRolFirmante(rol) {
  return ROLES_FIRMANTE[rol] || rol || 'Usuario autorizado'
}

export function puedeFirmarPedido(pedido, usuario) {
  if (!pedido || !usuario || pedido.estado !== 'PENDIENTE_FIRMA' || estadoFirmaPedido(pedido) === 'FIRMADO') {
    return false
  }

  // Solo firma el veterinario asignado al pedido (se compara el username).
  return usuario.rol === 'VETERINARIO'
    && Boolean(usuario.username)
    && usuario.username === pedido.veterinarioUsername
}

// Orden obligatorio: primero firma el veterinario asignado y después paga la clínica.
// Administración no paga en nombre del cliente (el backend también lo impide).
export function puedePagarPedido(pedido, usuario) {
  if (!pedido || pedido.estado !== 'FIRMADO' || !pedido.fechaFirma) return false
  if (usuario?.rol === 'ADMIN') return false
  return !pedido.fechaPago && estadoPagoPedido(pedido) !== 'PAGADO'
}
