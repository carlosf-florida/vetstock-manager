import { estadoPedido, estadoPago } from '../format.js'

export function BadgeEstadoPedido({ estado }) {
  const e = estadoPedido(estado)
  return (
    <span className={`etiqueta ${e.clase}`}>
      <span aria-hidden="true">{e.icono}</span> {e.texto}
    </span>
  )
}

export function BadgeEstadoPago({ estado }) {
  const e = estadoPago(estado)
  return (
    <span className={`etiqueta ${e.clase}`}>
      <span aria-hidden="true">{e.icono}</span> {e.texto}
    </span>
  )
}

export function BadgeActivo({ activo }) {
  return activo === false ? (
    <span className="etiqueta est-cancelado"><span aria-hidden="true">⛔</span> Desactivado</span>
  ) : (
    <span className="etiqueta est-expedido"><span aria-hidden="true">🟢</span> Activo</span>
  )
}

export function BadgeStockBajo({ bajo }) {
  if (!bajo) return null
  return (
    <span className="etiqueta est-pago-fallado"><span aria-hidden="true">⚠️</span> Stock bajo</span>
  )
}
