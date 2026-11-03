import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../api.js'
import { fecha, kg } from '../../format.js'
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

  async function eliminarBulto(b) {
    if (!window.confirm(`¿Eliminar el bulto ${b.identificador}? Sobra respecto al peso del pedido.`)) return
    try {
      await api.delete(`/api/bultos/${b.id}`)
      notificar({ texto: `Bulto ${b.identificador} eliminado.` })
      onCambio?.()
    } catch (err) {
      notificar({ tipo: 'error', texto: err.message })
    }
  }

  async function sacarDePalet(b) {
    if (!window.confirm(`¿Sacar el bulto ${b.identificador} del palet ${b.paletIdentificador}?`)) return
    try {
      await api.patch(`/api/bultos/${b.id}/quitar-palet`)
      notificar({ texto: `Bulto ${b.identificador} fuera del palet.` })
      cargar()
    } catch (err) {
      notificar({ tipo: 'error', texto: err.message })
    }
  }

  // Comparación entre registros y bultos calculados (el cálculo es del servidor)
  const calculados = pedido.bultosCalculados ?? null
  const registrados = bultos?.length ?? 0
  const faltan = calculados == null ? 0 : Math.max(0, calculados - registrados)
  const sobran = calculados == null ? 0 : Math.max(0, registrados - calculados)
  const eliminables = (bultos || []).filter((b) => !b.paletId).length

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
        {!pedido.salida && pedido.salidaHabitualClinica && ' Al marcar el pedido como preparado se le asignará la salida habitual, salvo que elijas otra aquí.'}
      </p>
      {faltaSalida && (
        <p className="caja-aviso">
          <span>La clínica no tiene salida habitual: <strong>elige una salida</strong> antes de marcar el pedido como preparado.</span>
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

      <h2>Peso y bultos</h2>
      {/* El peso y los bultos calculados vienen del servidor (máx. 25 kg por bulto) */}
      {calculados == null ? (
        <p className="caja-aviso">
          <span>
            <strong>Cálculo incompleto:</strong> falta el peso de {(pedido.productosSinPeso || []).join(', ')}.
            {Number(pedido.pesoTotalKg) > 0 && ` Peso conocido: ${kg(pedido.pesoTotalKg)} kg (el total real es mayor).`}
            {' '}Complétalo en <Link to="/admin/productos">Productos</Link>; sin peso no se puede preparar.
          </span>
        </p>
      ) : (
        <p>
          <strong>Peso total: {kg(pedido.pesoTotalKg)} kg</strong> · <strong>Bultos calculados: {calculados}</strong>
          <small className="texto-suave"> (hasta 25 kg por bulto; 25 kg exactos = 1 bulto)</small>
        </p>
      )}

      <ErrorBox mensaje={error} onReintentar={cargar} />
      {bultos === null && !error ? (
        <Spinner texto="Cargando bultos…" />
      ) : (
        <>
          <p>Registros de bultos: <strong>{registrados}</strong></p>
          {faltan > 0 && ['LISTO_PREPARAR', 'EN_PREPARACION'].includes(pedido.estado) && (
            <p className="texto-suave">
              Al marcar el pedido como preparado se crearán {faltan === 1 ? 'el bulto que falta' : `los ${faltan} bultos que faltan`} (VSM-{pedido.id}-B1…).
            </p>
          )}
          {faltan > 0 && pedido.estado === 'PREPARADO' && (
            <p className="caja-aviso">Faltan {faltan} {faltan === 1 ? 'bulto' : 'bultos'} respecto al peso actual: regístralos antes de expedir.</p>
          )}
          {sobran > 0 && (
            <p className="caja-aviso">
              <span>
                Hay {registrados} registros y por peso corresponden {calculados}. No se borra ninguno automáticamente.{' '}
                {eliminables >= sobran
                  ? `Elimina ${sobran === 1 ? 'el sobrante' : `${sobran} sobrantes`} entre los que no están en un palet.`
                  : `${eliminables === 0 ? 'Todos están en un palet' : eliminables === 1 ? 'Solo hay 1 bulto fuera de un palet' : `Solo hay ${eliminables} bultos fuera de un palet`}: saca del palet ${sobran === 1 ? 'el que sobre' : 'los que sobren'} («Sacar del palet») y después elimínalos. Hasta resolverlo, el pedido no se puede expedir.`}
              </span>
            </p>
          )}
          {registrados > 0 && (
            <div className="tabla-scroll">
              <table className="tabla">
                <thead><tr><th>Bulto</th><th>Palet</th><th>Salida del palet</th><th>Alta</th><th></th></tr></thead>
                <tbody>
                  {bultos.map((b) => (
                    <tr key={b.id}>
                      <td>{b.identificador}</td>
                      <td>{b.paletIdentificador || <span className="texto-suave">Sin palet</span>}</td>
                      <td>{b.paletSalida || '—'}</td>
                      <td>{fecha(b.fechaAlta)}</td>
                      <td>
                        {b.paletId && !['EXPEDIDO', 'CANCELADO'].includes(pedido.estado) && (
                          <button type="button" className="btn btn-mini btn-secundario" onClick={() => sacarDePalet(b)}>Sacar del palet</button>
                        )}
                        {sobran > 0 && !b.paletId && !['EXPEDIDO', 'CANCELADO'].includes(pedido.estado) && (
                          <button type="button" className="btn btn-mini btn-secundario" onClick={() => eliminarBulto(b)}>Eliminar sobrante</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {faltan > 0 && ['LISTO_PREPARAR', 'EN_PREPARACION', 'PREPARADO'].includes(pedido.estado) && (
            <Link className="btn btn-secundario" to={`/admin/bultos?pedidoId=${pedido.id}`}>Registrar bulto de este pedido</Link>
          )}
        </>
      )}
    </div>
  )
}
