import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../api.js'
import { fecha } from '../../format.js'
import { ErrorBox, Spinner } from '../Spinner.jsx'
import { useNotificaciones } from '../Notificaciones.jsx'

// Estados en los que administración puede asignar o cambiar la salida (nunca expedido o cancelado)
const ESTADOS_CON_SALIDA_EDITABLE = ['LISTO_PREPARAR', 'EN_PREPARACION', 'PREPARADO']

/**
 * Salida de expedición y bultos de un pedido (solo administración).
 * El número de bultos sale de los bultos registrados, nunca de un contador aparte.
 */
export default function LogisticaPedido({ pedido, onCambio }) {
  const [bultos, setBultos] = useState(null)
  const [salidas, setSalidas] = useState([])
  const [error, setError] = useState(null)
  const [salidaElegida, setSalidaElegida] = useState('')
  const [guardando, setGuardando] = useState(false)
  const { notificar } = useNotificaciones()

  async function cargar() {
    setError(null)
    try {
      const [b, s] = await Promise.all([api.get(`/api/bultos?pedidoId=${pedido.id}`), api.get('/api/destinos')])
      setBultos(Array.isArray(b) ? b : [])
      setSalidas(Array.isArray(s) ? s : [])
    } catch (err) {
      setError(err.message)
    }
  }

  // Se recarga cuando cambia el pedido (por ejemplo, tras prepararlo o registrar un bulto)
  useEffect(() => { cargar() }, [pedido.id, pedido.estado, pedido.bultos, pedido.salidaId])
  useEffect(() => { setSalidaElegida(pedido.salidaId ? String(pedido.salidaId) : '') }, [pedido.salidaId])

  async function asignarSalida() {
    if (!salidaElegida) return
    setGuardando(true)
    try {
      await api.patch(`/api/pedidos/${pedido.id}/salida`, { destinoId: Number(salidaElegida) })
      notificar({ texto: 'Salida del pedido guardada.' })
      onCambio?.()
    } catch (err) {
      notificar({ tipo: 'error', texto: err.message })
    } finally {
      setGuardando(false)
    }
  }

  const editable = ESTADOS_CON_SALIDA_EDITABLE.includes(pedido.estado)
  const faltaSalida = !pedido.salida && !pedido.salidaHabitualClinica && ['LISTO_PREPARAR', 'EN_PREPARACION'].includes(pedido.estado)

  return (
    <div className="tarjeta">
      <h2>Salida de expedición</h2>
      <p>
        <strong>Salida del pedido:</strong> {pedido.salida || <span className="texto-suave">Sin asignar</span>}
        {pedido.salida && pedido.salidaAsignadaPor && (
          <small className="texto-suave">
            {' '}· {pedido.salidaManual ? 'elegida' : 'asignada al preparar'} por {pedido.salidaAsignadaPor}
            {pedido.fechaAsignacionSalida && ` el ${fecha(pedido.fechaAsignacionSalida)}`}
          </small>
        )}
      </p>
      <p className="texto-suave">
        Salida habitual de la clínica: {pedido.salidaHabitualClinica || 'ninguna configurada'}.
        {!pedido.salida && ' Al marcar el pedido como preparado se le asignará la salida habitual, salvo que elijas otra aquí.'}
      </p>
      {faltaSalida && (
        <p className="caja-aviso">
          La clínica no tiene salida habitual: <strong>elige una salida</strong> antes de marcar el pedido como preparado.
        </p>
      )}
      {editable ? (
        <div className="acciones-fila">
          <select className="input" value={salidaElegida} onChange={(e) => setSalidaElegida(e.target.value)} aria-label="Salida del pedido">
            <option value="">— Elige una salida —</option>
            {salidas.map((d) => <option key={d.id} value={d.id}>{d.etiqueta}</option>)}
          </select>
          <button type="button" className="btn btn-secundario" onClick={asignarSalida}
            disabled={guardando || !salidaElegida || salidaElegida === String(pedido.salidaId)}>
            {pedido.salida ? 'Cambiar salida' : 'Asignar salida'}
          </button>
        </div>
      ) : (
        pedido.estado === 'EXPEDIDO' && <p className="texto-suave">Pedido expedido: su salida ya no se puede cambiar.</p>
      )}

      <h2>Bultos</h2>
      <ErrorBox mensaje={error} onReintentar={cargar} />
      {bultos === null && !error ? (
        <Spinner texto="Cargando bultos…" />
      ) : (
        <>
          <p><strong>Total: {bultos?.length ?? 0}</strong> {bultos?.length === 1 ? 'bulto' : 'bultos'} (paquetes, no unidades)</p>
          {bultos?.length > 0 && (
            <div className="tabla-scroll">
              <table className="tabla">
                <thead><tr><th>Bulto</th><th>Palet</th><th>Salida del palet</th><th>Alta</th></tr></thead>
                <tbody>
                  {bultos.map((b) => (
                    <tr key={b.id}>
                      <td>{b.identificador}</td>
                      <td>{b.paletIdentificador || <span className="texto-suave">Sin palet</span>}</td>
                      <td>{b.paletSalida || '—'}</td>
                      <td>{fecha(b.fechaAlta)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {['LISTO_PREPARAR', 'EN_PREPARACION', 'PREPARADO'].includes(pedido.estado) && (
            <Link className="btn btn-secundario" to={`/admin/bultos?pedidoId=${pedido.id}`}>Registrar bulto de este pedido</Link>
          )}
        </>
      )}
    </div>
  )
}
