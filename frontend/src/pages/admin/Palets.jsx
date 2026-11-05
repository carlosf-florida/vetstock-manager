import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import { useNotificaciones } from '../../components/Notificaciones.jsx'
import { ErrorBox, Spinner, SinDatos } from '../../components/Spinner.jsx'

export default function AdminPalets() {
  const [palets, setPalets] = useState(null)
  const [destinos, setDestinos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const { notificar } = useNotificaciones()

  const [form, setForm] = useState({ identificador: '', destinoId: '' })
  const [errores, setErrores] = useState({})
  const [guardando, setGuardando] = useState(false)
  const [detalle, setDetalle] = useState(null) // palet expandido
  const [cargandoDetalle, setCargandoDetalle] = useState(false)

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const [p, d] = await Promise.all([api.get('/api/palets'), api.get('/api/destinos')])
      setPalets(Array.isArray(p) ? p : [])
      setDestinos(Array.isArray(d) ? d : [])
    } catch (err) {
      setError(err.message)
      setPalets(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargar() }, [])

  function avisar(texto) {
    notificar({ texto })
  }

  async function crear(e) {
    e.preventDefault()
    const errs = {}
    if (!form.identificador.trim()) errs.identificador = 'Escribe un identificador para el palet.'
    if (!form.destinoId) errs.destinoId = 'Elige el destino del palet.'
    setErrores(errs)
    if (Object.keys(errs).length) return
    setGuardando(true)
    setError(null)
    try {
      await api.post('/api/palets', {
        identificador: form.identificador.trim(),
        destinoId: Number(form.destinoId),
      })
      setForm({ identificador: '', destinoId: '' })
      avisar('Palet creado.')
      cargar()
    } catch (err) {
      setError(err.message)
    } finally {
      setGuardando(false)
    }
  }

  async function verDetalle(palet) {
    if (detalle?.id === palet.id) { setDetalle(null); return }
    setCargandoDetalle(true)
    setError(null)
    try {
      const datos = await api.get(`/api/palets/${palet.id}`)
      setDetalle(datos)
    } catch (err) {
      setError(err.message)
    } finally {
      setCargandoDetalle(false)
    }
  }

  const bultosDetalle = detalle?.bultos || []

  return (
    <section>
      <h1>Palets de expedición</h1>
      <p className="texto-suave">Crea palets con destino y consulta los bultos y pedidos que contiene cada uno.</p>

      <ErrorBox mensaje={error} onReintentar={cargar} />

      <div className="tarjeta">
        <h2>Crear palet</h2>
        <form onSubmit={crear} noValidate>
          <div className="campo-trio">
            <label className="campo">
              <span>Identificador *</span>
              <input className="input" value={form.identificador} onChange={(e) => setForm({ ...form, identificador: e.target.value })} placeholder="PALET-0001" />
              {errores.identificador && <small className="error-campo">{errores.identificador}</small>}
            </label>
            <label className="campo">
              <span>Salida *</span>
              <select className="input" value={form.destinoId} onChange={(e) => setForm({ ...form, destinoId: e.target.value })}>
                <option value="">— Elige una salida —</option>
                {destinos.map((d) => <option key={d.id} value={d.id}>{d.etiqueta || d.nombre}</option>)}
              </select>
              {errores.destinoId && <small className="error-campo">{errores.destinoId}</small>}
            </label>
          </div>
          <button type="submit" className="btn btn-primario" disabled={guardando}>
            {guardando ? 'Creando…' : 'Crear palet'}
          </button>
        </form>
        <p className="texto-suave">¿Falta una salida? Créala en la sección <strong>Salidas</strong>. En un palet solo caben bultos de pedidos con su misma salida.</p>
      </div>

      <h2>Palets</h2>
      {cargando ? (
        <Spinner texto="Cargando palets…" />
      ) : !palets || palets.length === 0 ? (
        <SinDatos mensaje="Todavía no hay palets creados." />
      ) : (
        <div className="tabla-scroll">
          <table className="tabla">
            <thead>
              <tr><th>Identificador</th><th>Salida</th><th>Bultos</th><th>Fecha expedición</th><th></th></tr>
            </thead>
            <tbody>
              {palets.map((p) => (
                <tr key={p.id}>
                  <td>{p.identificador}</td>
                  <td>{p.destinoEtiqueta || p.destinoNombre || `Salida #${p.destinoId}`}</td>
                  <td>{p.bultos?.length ?? 0}</td>
                  <td>{p.fechaExpedicion || '—'}</td>
                  <td>
                    <button type="button" className="btn btn-mini btn-secundario" onClick={() => verDetalle(p)}>
                      {detalle?.id === p.id ? 'Ocultar detalle' : 'Ver bultos y pedidos'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {cargandoDetalle && <Spinner texto="Cargando detalle del palet…" />}
      {detalle && !cargandoDetalle && (
        <div className="tarjeta">
          <h2>Palet {detalle.identificador} → {detalle.destinoNombre}</h2>
          {bultosDetalle.length === 0 ? (
            <SinDatos mensaje="Este palet no tiene bultos asignados todavía. Asígnalos desde la sección Bultos." />
          ) : (
            <div className="tabla-scroll">
              <table className="tabla">
                <thead>
                  <tr><th>Bulto</th><th>Pedido</th><th>Clínica</th><th>Estado del pedido</th></tr>
                </thead>
                <tbody>
                  {bultosDetalle.map((b) => (
                    <tr key={b.id}>
                      <td>{b.identificador}</td>
                      <td>{b.pedidoId ? `#${b.pedidoId}` : '—'}</td>
                      <td>{b.pedido?.clinicaNombre || b.clinicaNombre || '—'}</td>
                      <td>{b.pedido?.estado || b.estadoPedido || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
