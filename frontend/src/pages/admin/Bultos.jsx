import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../../api.js'
import { NOMBRE_ESTADO_PEDIDO } from '../../format.js'
import Tabla from '../../components/Tabla.jsx'
import { ErrorBox, Spinner } from '../../components/Spinner.jsx'

export default function AdminBultos() {
  const [bultos, setBultos] = useState(null)
  const [palets, setPalets] = useState([])
  const [pedidos, setPedidos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [ok, setOk] = useState(null)

  // Desde el detalle de un pedido se llega con ?pedidoId=… y el pedido ya viene elegido
  const [paramsUrl] = useSearchParams()
  const pedidoDesdeUrl = paramsUrl.get('pedidoId') || ''
  const [form, setForm] = useState({ identificador: '', pedidoId: pedidoDesdeUrl })
  const [errores, setErrores] = useState({})
  const [guardando, setGuardando] = useState(false)
  const [asignando, setAsignando] = useState(null) // id del bulto en curso

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const [b, p, ps] = await Promise.all([
        api.get('/api/bultos'),
        api.get('/api/palets'),
        api.get('/api/pedidos'),
      ])
      setBultos(Array.isArray(b) ? b : [])
      setPalets(Array.isArray(p) ? p : [])
      setPedidos(Array.isArray(ps) ? ps : [])
    } catch (err) {
      setError(err.message)
      setBultos(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargar() }, [])

  function avisar(texto) {
    setOk(texto)
    setTimeout(() => setOk(null), 3500)
  }

  async function crear(e) {
    e.preventDefault()
    const errs = {}
    if (!form.identificador.trim()) errs.identificador = 'Escribe un identificador para el bulto.'
    if (!form.pedidoId) errs.pedidoId = 'Elige el pedido al que pertenece el bulto.'
    setErrores(errs)
    if (Object.keys(errs).length) return
    setGuardando(true)
    setError(null)
    try {
      await api.post('/api/bultos', {
        identificador: form.identificador.trim(),
        pedidoId: Number(form.pedidoId),
      })
      // Se mantiene el pedido elegido para poder registrar varios bultos seguidos
      setForm({ identificador: '', pedidoId: form.pedidoId })
      avisar(`Bulto registrado en el pedido #${form.pedidoId}.`)
      cargar()
    } catch (err) {
      setError(err.message)
    } finally {
      setGuardando(false)
    }
  }

  async function asignarPalet(bulto, paletId) {
    if (!paletId) return
    setAsignando(bulto.id)
    setError(null)
    try {
      await api.patch(`/api/bultos/${bulto.id}/asignar-palet`, { paletId: Number(paletId) })
      avisar(`Bulto asignado al palet seleccionado.`)
      cargar()
    } catch (err) {
      setError(err.message)
    } finally {
      setAsignando(null)
    }
  }

  return (
    <section>
      <h1>Bultos</h1>
      <p className="texto-suave">Registra los bultos de cada pedido y asígnalos a un palet de expedición.</p>

      {ok && <div className="caja-ok"><span aria-hidden="true">✔️</span> {ok}</div>}
      <ErrorBox mensaje={error} onReintentar={cargar} />

      <div className="tarjeta">
        <h2>Registrar nuevo bulto</h2>
        <form onSubmit={crear} noValidate>
          <div className="campo-trio">
            <label className="campo">
              <span>Identificador *</span>
              <input className="input" value={form.identificador} onChange={(e) => setForm({ ...form, identificador: e.target.value })} placeholder="BULTO-0001" />
              {errores.identificador && <small className="error-campo">{errores.identificador}</small>}
            </label>
            <label className="campo">
              <span>Pedido *</span>
              <select className="input" value={form.pedidoId} onChange={(e) => setForm({ ...form, pedidoId: e.target.value })}>
                <option value="">— Elige un pedido —</option>
                {/* Solo pedidos firmados y pagados que están en preparación admiten bultos */}
                {pedidos.filter((p) => ['LISTO_PREPARAR', 'EN_PREPARACION', 'PREPARADO'].includes(p.estado)).map((p) => (
                  <option key={p.id} value={p.id}>#{p.id} · {p.clinicaNombre || ''} · {NOMBRE_ESTADO_PEDIDO(p.estado)}</option>
                ))}
              </select>
              <small className="texto-suave">Solo aparecen pedidos firmados y pagados, listos o en preparación.</small>
              {errores.pedidoId && <small className="error-campo">{errores.pedidoId}</small>}
            </label>
          </div>
          <button type="submit" className="btn btn-primario" disabled={guardando}>
            {guardando ? 'Registrando…' : 'Registrar bulto'}
          </button>
        </form>
      </div>

      {pedidoDesdeUrl && (
        <p><Link to={`/admin/pedidos/${pedidoDesdeUrl}`}>← Volver al pedido #{pedidoDesdeUrl}</Link></p>
      )}
      <p className="texto-suave">Un bulto solo puede ir en un palet de la misma salida que su pedido.</p>

      <h2>Bultos registrados</h2>
      {cargando ? (
        <Spinner texto="Cargando bultos…" />
      ) : (
        <Tabla
          columns={[
            { key: 'identificador', label: 'Identificador' },
            { key: 'pedidoId', label: 'Pedido', render: (b) => (b.pedidoId ? <Link to={`/admin/pedidos/${b.pedidoId}`}>#{b.pedidoId}</Link> : '—') },
            { key: 'salidaPedido', label: 'Salida del pedido', render: (b) => b.salidaPedido || <span className="texto-suave">Sin asignar</span> },
            { key: 'palet', label: 'Palet asignado', render: (b) => b.paletId
              ? <>{b.paletIdentificador || `#${b.paletId}`}{b.paletSalida ? ` · ${b.paletSalida}` : ''}</>
              : <span className="etiqueta est-pago-pendiente"><span aria-hidden="true">⏳</span> Sin palet</span> },
            {
              key: 'asignar',
              label: 'Asignar a palet',
              render: (b) => (
                <select
                  className="input input-compacto"
                  value={b.paletId || ''}
                  onChange={(e) => asignarPalet(b, e.target.value)}
                  disabled={asignando === b.id}
                  aria-label={`Asignar el bulto ${b.identificador} a un palet`}
                >
                  <option value="">— Elige palet —</option>
                  {palets.map((p) => <option key={p.id} value={p.id}>{p.identificador} ({p.destinoEtiqueta || p.destinoNombre || 'sin salida'})</option>)}
                </select>
              ),
            },
          ]}
          filas={bultos || []}
          claveFila={(b) => b.id}
          busquedaCampos={['identificador', (b) => String(b.pedidoId ?? '')]}
          placeholderBusqueda="Buscar por identificador o nº de pedido…"
          textoVacio="Todavía no hay bultos registrados."
        />
      )}
    </section>
  )
}
