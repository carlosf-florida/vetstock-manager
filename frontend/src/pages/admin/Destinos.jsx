import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import Tabla from '../../components/Tabla.jsx'
import { ErrorBox, Spinner } from '../../components/Spinner.jsx'

export default function AdminDestinos() {
  const [destinos, setDestinos] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [nombre, setNombre] = useState('')
  const [errorForm, setErrorForm] = useState(null)
  const [ok, setOk] = useState(null)
  const [guardando, setGuardando] = useState(false)

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
    if (!nombre.trim()) { setErrorForm('Escribe el nombre del destino.'); return }
    setErrorForm(null)
    setGuardando(true)
    try {
      await api.post('/api/destinos', { nombre: nombre.trim() })
      setNombre('')
      setOk('Destino creado.')
      setTimeout(() => setOk(null), 3000)
      cargar()
    } catch (err) {
      setErrorForm(err.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section>
      <h1>Destinos de expedición</h1>
      <p className="texto-suave">Da de alta los destinos a los que se pueden asignar los palets.</p>

      {ok && <div className="caja-ok"><span aria-hidden="true">✔️</span> {ok}</div>}
      <ErrorBox mensaje={error} onReintentar={cargar} />

      <div className="tarjeta">
        <h2>Nuevo destino</h2>
        <form onSubmit={crear} noValidate className="acciones-fila">
          <label className="campo campo-crece">
            <span>Nombre del destino *</span>
            <input className="input" value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Valencia" />
          </label>
          <button type="submit" className="btn btn-primario" disabled={guardando}>
            {guardando ? 'Creando…' : 'Crear destino'}
          </button>
        </form>
        {errorForm && <p className="error-campo">{errorForm}</p>}
      </div>

      {cargando ? (
        <Spinner texto="Cargando destinos…" />
      ) : (
        <Tabla
          columns={[{ key: 'nombre', label: 'Destino' }]}
          filas={destinos || []}
          claveFila={(d) => d.id}
          busquedaCampos={['nombre']}
          placeholderBusqueda="Buscar destino…"
          textoVacio="Todavía no hay destinos dados de alta."
        />
      )}
    </section>
  )
}
