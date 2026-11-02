import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import Tabla from '../../components/Tabla.jsx'
import { ErrorBox, Spinner } from '../../components/Spinner.jsx'

function FormularioClinica({ onCreada }) {
  const [form, setForm] = useState({ nombre: '', direccion: '', telefono: '' })
  const [errores, setErrores] = useState({})
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)

  async function enviar(e) {
    e.preventDefault()
    const errs = {}
    if (!form.nombre.trim()) errs.nombre = 'El nombre de la clínica es obligatorio.'
    setErrores(errs)
    if (Object.keys(errs).length) return
    setGuardando(true)
    setError(null)
    try {
      await api.post('/api/clinicas', { ...form, nombre: form.nombre.trim() })
      setForm({ nombre: '', direccion: '', telefono: '' })
      onCreada()
    } catch (err) {
      setError(err.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <form onSubmit={enviar} noValidate className="formulario-creacion">
      <h3>Nueva clínica</h3>
      <div className="campo-trio">
        <label className="campo">
          <span>Nombre *</span>
          <input className="input" value={form.nombre} onChange={(e) => setForm({ ...form, nombre: e.target.value })} />
          {errores.nombre && <small className="error-campo">{errores.nombre}</small>}
        </label>
        <label className="campo">
          <span>Dirección</span>
          <input className="input" value={form.direccion} onChange={(e) => setForm({ ...form, direccion: e.target.value })} />
        </label>
        <label className="campo">
          <span>Teléfono</span>
          <input className="input" value={form.telefono} onChange={(e) => setForm({ ...form, telefono: e.target.value })} />
        </label>
      </div>
      <ErrorBox mensaje={error} />
      <button type="submit" className="btn btn-primario" disabled={guardando}>{guardando ? 'Creando…' : 'Crear clínica'}</button>
    </form>
  )
}

function FormularioCuenta({ tipo, clinicas, onCreada }) {
  const esVeterinario = tipo === 'veterinarios'
  const inicial = { username: '', password: '', nombreCompleto: '', clinicaId: '', numeroColegiado: '', telefono: '' }
  const [form, setForm] = useState(inicial)
  const [errores, setErrores] = useState({})
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const set = (campo) => (e) => setForm({ ...form, [campo]: e.target.value })

  async function enviar(e) {
    e.preventDefault()
    const errs = {}
    if (!form.username.trim()) errs.username = 'El usuario es obligatorio.'
    if (form.password.length < 6) errs.password = 'La contraseña debe tener al menos 6 caracteres.'
    if (!form.nombreCompleto.trim()) errs.nombreCompleto = 'El nombre completo es obligatorio.'
    if (!form.clinicaId) errs.clinicaId = 'Elige la clínica a la que pertenece.'
    if (esVeterinario && !form.numeroColegiado.trim()) errs.numeroColegiado = 'El número de colegiado es obligatorio.'
    setErrores(errs)
    if (Object.keys(errs).length) return
    setGuardando(true)
    setError(null)
    const cuerpo = {
      username: form.username.trim(),
      password: form.password,
      nombreCompleto: form.nombreCompleto.trim(),
      clinicaId: Number(form.clinicaId),
    }
    if (esVeterinario) {
      cuerpo.numeroColegiado = form.numeroColegiado.trim()
      cuerpo.telefono = form.telefono.trim() || null
    }
    try {
      await api.post(`/api/${tipo}`, cuerpo)
      setForm(inicial)
      onCreada()
    } catch (err) {
      setError(err.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <form onSubmit={enviar} noValidate className="formulario-creacion">
      <h3>{esVeterinario ? 'Nuevo veterinario' : 'Nueva auxiliar'}</h3>
      <div className="campo-trio">
        <label className="campo">
          <span>Usuario *</span>
          <input className="input" value={form.username} onChange={set('username')} />
          {errores.username && <small className="error-campo">{errores.username}</small>}
        </label>
        <label className="campo">
          <span>Contraseña * (mín. 6)</span>
          <input className="input" type="password" value={form.password} onChange={set('password')} autoComplete="new-password" />
          {errores.password && <small className="error-campo">{errores.password}</small>}
        </label>
        <label className="campo">
          <span>Nombre completo *</span>
          <input className="input" value={form.nombreCompleto} onChange={set('nombreCompleto')} />
          {errores.nombreCompleto && <small className="error-campo">{errores.nombreCompleto}</small>}
        </label>
      </div>
      <div className="campo-trio">
        <label className="campo">
          <span>Clínica *</span>
          <select className="input" value={form.clinicaId} onChange={set('clinicaId')}>
            <option value="">— Elige una clínica —</option>
            {(clinicas || []).map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
          {errores.clinicaId && <small className="error-campo">{errores.clinicaId}</small>}
        </label>
        {esVeterinario && (
          <>
            <label className="campo">
              <span>Nº de colegiado *</span>
              <input className="input" value={form.numeroColegiado} onChange={set('numeroColegiado')} placeholder="COL-0000" />
              {errores.numeroColegiado && <small className="error-campo">{errores.numeroColegiado}</small>}
            </label>
            <label className="campo">
              <span>Teléfono</span>
              <input className="input" value={form.telefono} onChange={set('telefono')} />
            </label>
          </>
        )}
      </div>
      <ErrorBox mensaje={error} />
      <button type="submit" className="btn btn-primario" disabled={guardando}>
        {guardando ? 'Creando…' : esVeterinario ? 'Crear veterinario' : 'Crear auxiliar'}
      </button>
    </form>
  )
}

export default function AdminClinicas() {
  const [clinicas, setClinicas] = useState(null)
  const [veterinarios, setVeterinarios] = useState(null)
  const [auxiliares, setAuxiliares] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)

  async function cargarTodo() {
    setCargando(true)
    setError(null)
    try {
      const [c, v, a] = await Promise.all([
        api.get('/api/clinicas'),
        api.get('/api/veterinarios'),
        api.get('/api/auxiliares'),
      ])
      setClinicas(Array.isArray(c) ? c : [])
      setVeterinarios(Array.isArray(v) ? v : [])
      setAuxiliares(Array.isArray(a) ? a : [])
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargarTodo() }, [])

  const nombreClinica = (id) => clinicas?.find((c) => c.id === id)?.nombre || `Clínica #${id}`

  async function cambiarResponsable(clinicaId, e) {
    const veterinarioId = e.target.value
    if (!veterinarioId) return
    try {
      await api.put(`/api/clinicas/${clinicaId}/responsable`, { veterinarioId: Number(veterinarioId) })
      await cargarTodo()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <section>
      <h1>Clínicas y cuentas de acceso</h1>
      <p className="texto-suave">Gestiona las clínicas cliente y sus veterinarios y auxiliares.</p>

      <ErrorBox mensaje={error} onReintentar={cargarTodo} />
      {cargando && <Spinner texto="Cargando clínicas y cuentas…" />}

      {!cargando && !error && (
        <>
          <div className="tarjeta">
            <h2>Clínicas</h2>
            <FormularioClinica onCreada={cargarTodo} />
            {clinicas?.length === 0 ? (
              <p className="texto-suave">Todavía no hay clínicas dadas de alta.</p>
            ) : (
              <Tabla
                columns={[
                  { key: 'nombre', label: 'Nombre' },
                  { key: 'direccion', label: 'Dirección' },
                  { key: 'telefono', label: 'Teléfono' },
                  { key: 'responsable', label: 'Veterinario responsable', render: (c) => (
                    <select
                      className="input"
                      value={c.veterinarioResponsableId || ''}
                      onChange={(e) => cambiarResponsable(c.id, e)}
                    >
                      <option value="">— Sin asignar —</option>
                      {(veterinarios || []).filter((v) => v.clinicaId === c.id).map((v) => (
                        <option key={v.id} value={v.id}>{v.nombreCompleto || v.username}</option>
                      ))}
                    </select>
                  ) },
                  { key: 'activa', label: 'Estado', render: (c) => c.activa === false
                    ? <span className="etiqueta est-cancelado">⛔ Inactiva</span>
                    : <span className="etiqueta est-expedido">🟢 Activa</span> },
                ]}
                filas={clinicas || []}
                claveFila={(c) => c.id}
                busquedaCampos={['nombre', 'direccion', 'telefono']}
                placeholderBusqueda="Buscar clínica…"
                textoVacio="No hay clínicas que coincidan."
              />
            )}
          </div>

          <div className="tarjeta">
            <h2>Veterinarios (pueden firmar pedidos)</h2>
            <FormularioCuenta tipo="veterinarios" clinicas={clinicas} onCreada={cargarTodo} />
            <Tabla
              columns={[
                { key: 'nombreCompleto', label: 'Nombre' },
                { key: 'username', label: 'Usuario' },
                { key: 'clinica', label: 'Clínica', render: (v) => v.clinicaNombre || nombreClinica(v.clinicaId) },
                { key: 'numeroColegiado', label: 'Nº colegiado' },
                { key: 'telefono', label: 'Teléfono', soloEscritorio: true },
              ]}
              filas={veterinarios || []}
              claveFila={(v) => v.id}
              busquedaCampos={['nombreCompleto', 'username', 'numeroColegiado', 'clinicaNombre']}
              placeholderBusqueda="Buscar veterinario…"
              textoVacio="No hay veterinarios de alta."
            />
          </div>

          <div className="tarjeta">
            <h2>Auxiliares (preparan pedidos, no firman)</h2>
            <FormularioCuenta tipo="auxiliares" clinicas={clinicas} onCreada={cargarTodo} />
            <Tabla
              columns={[
                { key: 'nombreCompleto', label: 'Nombre' },
                { key: 'username', label: 'Usuario' },
                { key: 'clinica', label: 'Clínica', render: (a) => a.clinicaNombre || nombreClinica(a.clinicaId) },
              ]}
              filas={auxiliares || []}
              claveFila={(a) => a.id}
              busquedaCampos={['nombreCompleto', 'username', 'clinicaNombre']}
              placeholderBusqueda="Buscar auxiliar…"
              textoVacio="No hay auxiliares de alta."
            />
          </div>
        </>
      )}
    </section>
  )
}
