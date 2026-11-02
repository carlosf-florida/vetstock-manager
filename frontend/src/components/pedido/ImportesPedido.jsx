import { totalPedido } from '../../format.js'

function esImporteDisponible(valor) {
  return valor !== null && valor !== undefined && valor !== '' && Number.isFinite(Number(valor))
}

function formatoImporte(importe, moneda) {
  return `${Number(importe).toFixed(2)} ${moneda}`
}

function formatoPorcentaje(porcentaje) {
  return Number(porcentaje).toLocaleString('es-ES', { maximumFractionDigits: 2 })
}

export default function ImportesPedido({ pedido, importes }) {
  const datos = importes ?? pedido ?? {}
  const total = totalPedido(datos)
  const moneda = datos.moneda || pedido?.moneda || 'EUR'
  const tieneDesglose = [datos.baseImponible, datos.ivaPorcentaje, datos.importeIva].every(esImporteDisponible)

  return (
    <div className="importes-pedido">
      {tieneDesglose && (
        <>
          <div className="importes-pedido-linea">
            <span>Base imponible</span>
            <strong>{formatoImporte(datos.baseImponible, moneda)}</strong>
          </div>
          <div className="importes-pedido-linea">
            <span>IVA {formatoPorcentaje(datos.ivaPorcentaje)}%</span>
            <strong>{formatoImporte(datos.importeIva, moneda)}</strong>
          </div>
        </>
      )}
      <div className="importes-pedido-linea importes-pedido-total">
        <span>Total a pagar</span>
        <strong>{esImporteDisponible(total) ? formatoImporte(total, moneda) : '—'}</strong>
      </div>
    </div>
  )
}
