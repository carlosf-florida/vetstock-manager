import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import { ErrorBox, Spinner } from '../../components/Spinner.jsx'
import Tabla from '../../components/Tabla.jsx'

const REVISION = { PENDIENTE: 'Pendiente', REQUIERE_INFORMACION: 'Requiere información', APROBADA: 'Aprobada', RECHAZADA: 'Rechazada' }
const DOCUMENTO = { SIN_DOCUMENTO: 'Anterior, sin documento', PENDIENTE: 'Pendiente de revisión', ACEPTADO: 'Aceptado', EXCEPCION: 'Excepción autorizada' }
const ALTA = { NO_INICIADA: 'No iniciada', EN_PROCESO: 'En proceso', REQUIERE_REVISION: 'Requiere revisión', FALLIDA: 'Fallida', COMPLETADA: 'Completada' }

function textoAcceso(s) {
  if (s.accesoActivo === null) return s.estadoRevision === 'APROBADA' ? 'Sin cuenta todavía' : '—'
  return s.accesoActivo ? 'Acceso activo' : 'Acceso pendiente'
}

function textoOdoo(s) {
  if (s.odooUltimoError) return 'Error, se reintentará'
  return s.odooPendiente ? 'Pendiente de enviar' : 'Enviada'
}

/**
 * Consulta de solicitudes. La decisión se toma en Odoo; aquí se sigue el estado, se reintenta lo que
 * quedó a medias y se genera el enlace de activación. Aprobar la clínica no activa la cuenta.
 */
export default function AdminSolicitudes() {
  const [solicitudes, setSolicitudes] = useState(null)
  const [error, setError] = useState(null)
  const [errorAccion, setErrorAccion] = useState(null)
  const [ocupada, setOcupada] = useState(null)
  const [enlace, setEnlace] = useState(null)

  async function cargar() {
    try {
      setSolicitudes(await api.get('/api/solicitudes-alta'))
      setError(null)
    } catch (err) {
      setError(err.message)
    }
  }
  useEffect(() => { cargar() }, [])

  async function accion(s, ruta, alTerminar) {
    setOcupada(s.id)
    setErrorAccion(null)
    try {
      const r = await api.post(`/api/solicitudes-alta/${s.id}/${ruta}`, {})
      alTerminar?.(r)
      await cargar()
    } catch (err) {
      setErrorAccion(`${s.referencia}: ${err.message}`)
    } finally {
      setOcupada(null)
    }
  }

  const reintentar = (s) => accion(s, 'reintentar')
  // La clínica envía el nuevo documento por el canal habitual; aquí lo sube administración
  async function sustituirDocumento(s, archivo) {
    if (!archivo) return
    const cuerpo = new FormData()
    cuerpo.append('documento', archivo)
    setOcupada(s.id)
    setErrorAccion(null)
    try {
      await api.postForm(`/api/solicitudes-alta/${s.id}/documento`, cuerpo)
      await cargar()
    } catch (err) {
      setErrorAccion(`${s.referencia}: ${err.message}`)
    } finally {
      setOcupada(null)
    }
  }

  const generarEnlace = (s) => accion(s, 'activacion', (r) => setEnlace({
    referencia: s.referencia, usuario: r.usuario, caduca: r.caduca,
    url: `${window.location.origin}/activar/${r.token}`,
  }))

  const columnas = [
    { key: 'referencia', label: 'Referencia' },
    { key: 'fechaCreacion', label: 'Fecha', soloEscritorio: true, render: (s) => new Date(s.fechaCreacion).toLocaleDateString('es-ES') },
    { key: 'clinicaNombre', label: 'Clínica' },
    { key: 'vetNombre', label: 'Veterinario propuesto' },
    { key: 'estadoRevision', label: 'Revisión', render: (s) => REVISION[s.estadoRevision] },
    { key: 'estadoAlta', label: 'Alta', render: (s) => <span title={s.mensajeAlta || undefined}>{ALTA[s.estadoAlta]}</span> },
    {
      key: 'documento', label: 'Documento', render: (s) => {
        const decidida = ['APROBADA', 'RECHAZADA'].includes(s.estadoRevision)
        return (
          <span>
            {DOCUMENTO[s.documentoEstado]}
            {s.documentoId && <small>{s.documentoEnOdoo ? 'En Odoo' : 'Pendiente de llegar a Odoo'}</small>}
            <span className="acciones-fila">
              {s.documentoId && <a className="btn btn-mini btn-secundario" href={`/api/solicitudes-alta/${s.id}/documento`} target="_blank" rel="noreferrer">Ver</a>}
              {!decidida && (
                <label className="btn btn-mini btn-secundario">
                  {s.documentoId ? 'Sustituir' : 'Adjuntar'}
                  <input type="file" accept="image/jpeg,image/png" className="pub-archivo-oculto" disabled={ocupada === s.id}
                    onChange={(e) => { sustituirDocumento(s, e.target.files?.[0]); e.target.value = '' }} />
                </label>
              )}
            </span>
          </span>
        )
      },
    },
    { key: 'odoo', label: 'Envío a Odoo', render: textoOdoo },
    { key: 'acceso', label: 'Acceso', render: textoAcceso },
    {
      key: 'acciones', label: 'Acciones', render: (s) => {
        const puedeReintentar = s.odooPendiente || (s.estadoRevision === 'APROBADA' && s.estadoAlta !== 'COMPLETADA')
        const puedeGenerar = s.veterinarioId && s.accesoActivo === false && s.estadoAlta === 'COMPLETADA'
        return (
          <span className="acciones-fila">
            {puedeReintentar && <button className="btn btn-mini btn-secundario" disabled={ocupada === s.id} onClick={() => reintentar(s)}>Reintentar</button>}
            {puedeGenerar && <button className="btn btn-mini btn-primario" disabled={ocupada === s.id} onClick={() => generarEnlace(s)}>Generar enlace</button>}
          </span>
        )
      },
    },
  ]

  if (error) return <ErrorBox mensaje={error} />
  if (!solicitudes) return <Spinner />

  return (
    <div>
      <h1>Solicitudes de alta</h1>
      <p className="texto-suave">La revisión (aceptar el documento, aprobar, rechazar o pedir información) se hace en Odoo, en Ventas → Solicitudes de alta de clínicas.
        Si la clínica envía otro documento por el canal habitual, adjúntalo aquí: la revisión anterior del documento deja de valer.</p>
      {errorAccion && <p className="caja-error" role="alert">{errorAccion}</p>}
      {enlace && (
        <div className="caja-aviso" role="status">
          <p><strong>Enlace de activación de {enlace.referencia}</strong>, para el usuario <strong>{enlace.usuario}</strong>.
            Cópialo ahora: no se vuelve a mostrar. Caduca el {new Date(enlace.caduca).toLocaleDateString('es-ES')} y solo sirve una vez.
            Entrégalo directamente a la persona; generar otro anula este.</p>
          <input className="input" readOnly value={enlace.url} aria-label="Enlace de activación" onFocus={(e) => e.target.select()} />
        </div>
      )}
      <Tabla
        columns={columnas}
        filas={solicitudes}
        claveFila={(s) => s.id}
        busquedaCampos={['referencia', 'clinicaNombre', 'vetNombre']}
        placeholderBusqueda="Buscar por referencia, clínica o veterinario…"
        textoVacio="Todavía no hay solicitudes."
      />
    </div>
  )
}
