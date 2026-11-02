import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../../api.js'
import { dinero } from '../../format.js'
import { BadgeEstadoPago } from '../Badges.jsx'
import { ErrorBox, Spinner } from '../Spinner.jsx'
import { infoEstadoPago } from '../../pedidoEstado.js'
import ImportesPedido from './ImportesPedido.jsx'

/**
 * Estado del pago y entrada a la pasarela simulada. Solo se puede iniciar
 * cuando el pedido está FIRMADO y aún no tiene fecha de pago.
 */
export default function PagoSeccion({ pedido, puedePagar }) {
  const navigate = useNavigate()
  const [pago, setPago] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [iniciando, setIniciando] = useState(false)
  const estadoPago = infoEstadoPago(pedido)
  const pagoEnCurso = pago?.estado === 'PENDIENTE' && Boolean(pago.referencia)

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      // La API devuelve los intentos ordenados del más reciente al más antiguo.
      const datos = await api.get(`/api/pagos?pedidoId=${pedido.id}`)
      setPago(Array.isArray(datos) ? datos[0] || null : null)
    } catch (err) {
      setError(err.message)
      setPago(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargar() }, [pedido.id, pedido.estado, pedido.fechaPago, pedido.estadoPago])

  function abrirPasarela(datos) {
    if (!datos?.referencia) throw new Error('No se ha recibido la referencia del pago. Consulta de nuevo su estado.')
    navigate(datos.urlSimulada || `/pago-simulado/${encodeURIComponent(datos.referencia)}`, {
      state: {
        pedidoId: pedido.id,
        importe: datos.importe,
        moneda: datos.moneda,
        importes: {
          baseImponible: pedido.baseImponible,
          ivaPorcentaje: pedido.ivaPorcentaje,
          importeIva: pedido.importeIva,
          total: datos.importe,
          moneda: datos.moneda,
        },
        volver: window.location.pathname,
      },
    })
  }

  async function iniciarPago() {
    if (iniciando || cargando) return
    setIniciando(true)
    setError(null)
    try {
      // Un intento pendiente se continúa; no se intenta crear un segundo pago.
      if (pagoEnCurso) {
        abrirPasarela(pago)
        return
      }
      const datos = await api.post(`/api/pagos/${pedido.id}/iniciar`)
      abrirPasarela(datos)
    } catch (err) {
      setError(err.message)
    } finally {
      setIniciando(false)
    }
  }

  return (
    <div>
      <ImportesPedido pedido={pedido} />
      {cargando ? (
        <Spinner texto="Consultando el estado del pago…" />
      ) : (
        <div className="pago-resumen">
          {pago ? (
            <>
              <BadgeEstadoPago estado={pago.estado} />
              {pago.referencia && <span className="texto-suave">Referencia: <code>{pago.referencia}</code></span>}
              {pago.importe != null && <span className="texto-suave">Importe: <strong>{dinero(pago.importe)}</strong></span>}
            </>
          ) : (
            !error && <span className={`etiqueta ${estadoPago.clase}`}>{estadoPago.texto}</span>
          )}
        </div>
      )}

      <ErrorBox mensaje={error} onReintentar={cargar} />

      {puedePagar && pago?.estado !== 'CONFIRMADO' && (
        <div className="acciones-fila">
          <button type="button" className="btn btn-primario" onClick={iniciarPago} disabled={iniciando || cargando}>
            {iniciando ? 'Abriendo pasarela…' : pagoEnCurso ? 'Continuar pago pendiente' : 'Pagar (entorno de pruebas)'}
          </button>
        </div>
      )}
      {pagoEnCurso && puedePagar && (
        <p className="texto-suave">Ya hay un intento iniciado. Puedes retomarlo con la misma referencia, sin crear otro pago.</p>
      )}
      {pedido.estado === 'PENDIENTE_FIRMA' && (
        <p className="texto-suave">El pago se habilita cuando el veterinario asignado revise y firme el pedido.</p>
      )}
      <p className="texto-suave">
        Pasarela de <strong>demostración</strong>: no se realiza ningún cobro real. El pedido solo se marca como pagado
        cuando el backend recibe y verifica la notificación firmada del proveedor.
      </p>
      {pago?.estado === 'FALLADO' && (
        <p className="texto-suave">El último intento de pago falló. Puedes reintentarlo: se generará una referencia nueva.</p>
      )}
    </div>
  )
}
