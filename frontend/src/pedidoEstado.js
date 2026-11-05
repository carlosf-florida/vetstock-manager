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

// El backend calcula pedido.autorizacion (firma válida, pago completo y clasificación).
// Una firma solo es válida si la hizo el veterinario asignado sobre el contenido actual.
export function firmaValida(pedido) {
  if (pedido?.autorizacion) return pedido.autorizacion.firmaValida
  return estadoFirmaPedido(pedido) === 'FIRMADO'
}

export function infoEstadoFirma(pedido) {
  if (firmaValida(pedido)) return { texto: 'Firma válida', clase: 'est-firmado' }
  // Un pedido cancelado ya no espera nada: «pendiente» sería contradictorio
  if (pedido?.estado === 'CANCELADO' && !pedido?.fechaFirma) return { texto: 'Sin firma', clase: 'est-neutro' }
  if (pedido?.fechaFirma) return { texto: 'Firma no válida', clase: 'est-pago-fallado' }
  return { texto: 'Pendiente de firma', clase: 'est-pendiente-firma' }
}

// Pedido que todavía necesita la firma VÁLIDA del veterinario asignado (sin firma, o con una firma
// que no vale: de otra persona o de un contenido anterior), sea cual sea su estado logístico.
export function necesitaFirma(pedido) {
  return ['PENDIENTE_FIRMA', 'FIRMADO', 'LISTO_PREPARAR'].includes(pedido?.estado) && !firmaValida(pedido)
}

// Enviado a firma (firmado o no) y sin pago confirmado
export function necesitaPago(pedido) {
  return ['PENDIENTE_FIRMA', 'FIRMADO'].includes(pedido?.estado) && !pedido?.fechaPago
}

export function enPreparacion(pedido) {
  return ['EN_PREPARACION', 'PREPARADO'].includes(pedido?.estado)
}

/** Situaciones del inicio y de Mis pedidos (?situacion=...): misma regla en los dos sitios. */
export const SITUACIONES = {
  FIRMA: { texto: 'Falta la firma del veterinario', cumple: (p) => necesitaFirma(p) },
  PAGO: { texto: 'Falta el pago', cumple: (p) => necesitaPago(p) },
  PREPARACION: { texto: 'En preparación', cumple: (p) => enPreparacion(p) },
  LISTO: { texto: 'Listo para preparar (firma válida y pago)', cumple: (p) => Boolean(p?.autorizacion?.listoParaPreparar) },
}

// Pestañas de administración, las mismas que los apartados de Odoo: un paso del trabajo en cada una.
// La clasificación por firma y pago la calcula el backend (pedido.autorizacion).
export const APARTADOS_PEDIDOS = {
  PENDIENTE_FIRMA: {
    texto: 'Pagados pendientes de firma',
    ayuda: 'Pago completo confirmado, pero falta la firma válida del veterinario asignado: todavía no se pueden preparar. La columna «Firma» indica qué falta.',
    cumple: (p) => p.autorizacion?.clasificacion === 'PAGADO_PENDIENTE_FIRMA',
  },
  LISTOS: {
    texto: 'Listos para preparar',
    ayuda: 'Firma válida y pago completo: se puede empezar a prepararlos.',
    cumple: (p) => Boolean(p.autorizacion?.listoParaPreparar),
  },
  EN_PREPARACION: {
    texto: 'En preparación',
    ayuda: 'Se están preparando. Al marcarlos como preparados se crean sus bultos y se asigna la salida.',
    cumple: (p) => p.estado === 'EN_PREPARACION',
  },
  PREPARADOS: {
    texto: 'Preparados',
    ayuda: 'Listos para salir: filtra por salida para ver lo que va en cada una y asigna los bultos a su palet.',
    cumple: (p) => p.estado === 'PREPARADO',
  },
  EXPEDIDOS: {
    texto: 'Expedidos',
    ayuda: 'Ya han salido. Filtra por salida para consultarlos.',
    cumple: (p) => p.estado === 'EXPEDIDO',
  },
  OTROS: {
    texto: 'Otros',
    ayuda: 'Borradores, pedidos sin pago completo y cancelados.',
    cumple: (p) => p.autorizacion?.clasificacion === 'OTROS',
  },
}

export function infoEstadoPago(pedido) {
  const estado = estadoPagoPedido(pedido)
  if (estado === 'PAGADO') return { texto: 'Pagado', clase: 'est-pago-confirmado' }
  if (pedido?.estado === 'CANCELADO') return { texto: 'Sin pago', clase: 'est-neutro' }
  if (estado === 'FALLADO') return { texto: 'Pago fallido', clase: 'est-pago-fallado' }
  return { texto: 'Pendiente', clase: 'est-pago-pendiente' }
}

export function nombreRolFirmante(rol) {
  return ROLES_FIRMANTE[rol] || rol || 'Usuario autorizado'
}

// Se puede firmar antes o después del pago, y sustituir una firma no válida, mientras no
// haya empezado la preparación. El backend vuelve a comprobarlo todo.
export function puedeFirmarPedido(pedido, usuario) {
  if (!pedido || !usuario || !['PENDIENTE_FIRMA', 'FIRMADO', 'LISTO_PREPARAR'].includes(pedido.estado) || firmaValida(pedido)) {
    return false
  }

  // Solo firma el veterinario asignado al pedido (se compara el username).
  return usuario.rol === 'VETERINARIO'
    && Boolean(usuario.username)
    && usuario.username === pedido.veterinarioUsername
}

// Se puede pagar en cuanto el pedido se envía a firma (antes o después de firmar).
// Administración no paga en nombre del cliente (el backend también lo impide).
export function puedePagarPedido(pedido, usuario) {
  if (!pedido || !['PENDIENTE_FIRMA', 'FIRMADO'].includes(pedido.estado)) return false
  if (usuario?.rol === 'ADMIN') return false
  return !pedido.fechaPago && estadoPagoPedido(pedido) !== 'PAGADO'
}
