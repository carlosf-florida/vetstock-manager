import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import Tabla from '../../components/Tabla.jsx'
import { ErrorBox, Spinner } from '../../components/Spinner.jsx'
import { useNotificaciones } from '../../components/Notificaciones.jsx'

/**
 * Salidas de expedición (en la base de datos, tabla «destinos»). Cada palet va a una salida
 * y cada clínica puede tener una salida habitual. El código es el del negocio (p. ej. «14»),
 * no el id interno, y es opcional: solo se pone cuando está confirmado.
 */
export default function AdminDestinos() {
  const [destinos, setDestinos] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [nuevo, setNuevo] = useState({ codigo: '', nombre: '' })
  const [errorForm, setErrorForm] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [editando, setEditando] = useState(null) // { id, codigo, nombre }
  const { notificar } = useNotificaciones()

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const datos = await api.get('/api/destinos')
      setDestinos(Array.isArray(datos) ? datos : [])
    } catch (err) {
      setError(err.message)
      setDestinos(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargar() }, [])

  async function crear(e) {
    e.preventDefault()
    if (!nuevo.nombre.trim()) { setErrorForm('Escribe el nombre de la salida.'); return }
    setErrorForm(null)
    setGuardando(true)
    try {
      await api.post('/api/destinos', { codigo: nuevo.codigo.trim() || null, nombre: nuevo.nombre.trim() })
      setNuevo({ codigo: '', nombre: '' })
      notificar({ texto: 'Salida creada.' })
      cargar()
    } catch (err) {
      setErrorForm(err.message)
    } finally {
      setGuardando(false)
    }
  }

  async function guardarEdicion() {
    if (!editando.nombre.trim()) { notificar({ tipo: 'error', texto: 'El nombre no puede quedar vacío.' }); return }
    setGuardando(true)
    try {
      await api.put(`/api/destinos/${editando.id}`, { codigo: editando.codigo.trim() || null, nombre: editando.nombre.trim() })
      notificar({ texto: 'Salida actualizada.' })
      setEditando(null)
      cargar()
    } catch (err) {
      notificar({ tipo: 'error', texto: err.message })
    } finally {
      setGuardando(false)
    }
  }

  const enEdicion = (d) => editando?.id === d.id

  return (
    <section>
      <h1>Salidas de expedición</h1>
      <p className="texto-suave">
        Salidas (destinos) a las que se asignan los palets y la salida habitual de cada clínica.
        El código es el que usa el almacén (por ejemplo «14»); déjalo vacío si no está confirmado.
      </p>

      <ErrorBox mensaje={error} onReintentar={cargar} />

      <div className="tarjeta">
        <h2>Nueva salida</h2>
        <form onSubmit={crear} noValidate className="acciones-fila">
          <label className="campo">
            <span>Código</span>
            <input className="input" maxLength={10} value={nuevo.codigo} onChange={(e) => setNuevo({ ...nuevo, codigo: e.target.value })} placeholder="14" />
          </label>
          <label className="campo campo-crece">
            <span>Nombre *</span>
            <input className="input" value={nuevo.nombre} onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })} placeholder="Regional" />
          </label>
          <button type="submit" className="btn btn-primario" disabled={guardando}>
            {guardando ? 'Guardando…' : 'Crear salida'}
          </button>
        </form>
        {errorForm && <p className="error-campo">{errorForm}</p>}
      </div>

      {cargando ? (
        <Spinner texto="Cargando salidas…" />
      ) : (
        <Tabla
          columns={[
            {
              key: 'codigo', label: 'Código', render: (d) => (enEdicion(d)
                ? <input className="input input-cantidad" maxLength={10} value={editando.codigo} aria-label="Código de la salida"
                    onChange={(e) => setEditando({ ...editando, codigo: e.target.value })} />
                : (d.codigo || <span className="texto-suave">Sin código</span>)),
            },
            {
              key: 'nombre', label: 'Nombre', render: (d) => (enEdicion(d)
                ? <input className="input" value={editando.nombre} aria-label="Nombre de la salida"
                    onChange={(e) => setEditando({ ...editando, nombre: e.target.value })} />
                : d.nombre),
            },
            {
              key: 'acciones', label: '', render: (d) => (enEdicion(d) ? (
                <span className="acciones-fila">
                  <button type="button" className="btn btn-mini btn-primario" onClick={guardarEdicion} disabled={guardando}>Guardar</button>
                  <button type="button" className="btn btn-mini btn-secundario" onClick={() => setEditando(null)}>Cancelar</button>
                </span>
              ) : (
                <button type="button" className="btn btn-mini btn-secundario"
                  onClick={() => setEditando({ id: d.id, codigo: d.codigo || '', nombre: d.nombre })}>Editar</button>
              )),
            },
          ]}
          filas={destinos || []}
          claveFila={(d) => d.id}
          busquedaCampos={['nombre', 'codigo']}
          placeholderBusqueda="Buscar salida…"
          textoVacio="Todavía no hay salidas dadas de alta."
        />
      )}
    </section>
  )
}
