import { useRef, useState } from 'react'
import { api } from '../../api.js'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const TELEFONO = /^[+0-9 ()-]{6,20}$/
const CODIGO_POSTAL = /^[A-Za-z0-9 -]{3,10}$/

// Las mismas reglas que comprueba el servidor; aquí solo sirven para avisar antes de enviar
const CLINICA = [
  { nombre: 'clinicaNombre', etiqueta: 'Nombre de la clínica', obligatorio: true, max: 100, autoComplete: 'organization' },
  { nombre: 'razonSocial', etiqueta: 'Razón social', max: 120, ayuda: 'Si es distinta del nombre.' },
  { nombre: 'identificadorFiscal', etiqueta: 'Identificador fiscal', max: 20, ayuda: 'Si corresponde en tu país.' },
  { nombre: 'email', etiqueta: 'Email de la clínica', obligatorio: true, max: 120, tipo: 'email', formato: EMAIL, autoComplete: 'email' },
  { nombre: 'telefono', etiqueta: 'Teléfono', obligatorio: true, max: 20, tipo: 'tel', formato: TELEFONO, autoComplete: 'tel' },
  { nombre: 'direccion', etiqueta: 'Dirección', obligatorio: true, max: 70, autoComplete: 'street-address' },
  { nombre: 'codigoPostal', etiqueta: 'Código postal', obligatorio: true, max: 10, formato: CODIGO_POSTAL, autoComplete: 'postal-code' },
  { nombre: 'localidad', etiqueta: 'Localidad', obligatorio: true, max: 30, autoComplete: 'address-level2' },
  { nombre: 'provincia', etiqueta: 'Provincia o región', obligatorio: true, max: 30, autoComplete: 'address-level1' },
  { nombre: 'pais', etiqueta: 'País', obligatorio: true, max: 40, autoComplete: 'country-name' },
]
const VETERINARIO = [
  { nombre: 'vetNombre', etiqueta: 'Nombre y apellidos', obligatorio: true, max: 100, autoComplete: 'name' },
  { nombre: 'vetColegiado', etiqueta: 'Número de colegiado', obligatorio: true, max: 20 },
  { nombre: 'vetColegio', etiqueta: 'Colegio o provincia', obligatorio: true, max: 60 },
  { nombre: 'vetEmail', etiqueta: 'Email profesional', obligatorio: true, max: 120, tipo: 'email', formato: EMAIL },
  { nombre: 'vetTelefono', etiqueta: 'Teléfono', max: 20, tipo: 'tel', formato: TELEFONO },
]
const CAMPOS = [...CLINICA, ...VETERINARIO]
const MAX_OBSERVACIONES = 500

function validar(datos, privacidad) {
  const errores = {}
  for (const c of CAMPOS) {
    const valor = (datos[c.nombre] || '').trim()
    if (c.obligatorio && !valor) errores[c.nombre] = 'Este campo es obligatorio.'
    else if (valor && c.formato && !c.formato.test(valor)) errores[c.nombre] = 'El formato no es válido.'
  }
  if (!privacidad) errores.privacidad = 'Confirma que has leído la información de privacidad.'
  return errores
}

function Campo({ campo, valor, error, onChange }) {
  const id = `solicitud-${campo.nombre}`
  const idAyuda = `${id}-ayuda`
  return (
    <label className="campo" htmlFor={id}>
      <span>{campo.etiqueta}{campo.obligatorio ? <abbr title="obligatorio"> *</abbr> : <em className="pub-opcional"> (opcional)</em>}</span>
      <input
        id={id} name={campo.nombre} className="input" type={campo.tipo || 'text'} maxLength={campo.max}
        autoComplete={campo.autoComplete || 'off'} value={valor} onChange={onChange}
        aria-required={campo.obligatorio ? 'true' : undefined} aria-invalid={error ? 'true' : undefined}
        aria-describedby={error || campo.ayuda ? idAyuda : undefined}
      />
      {error ? <small id={idAyuda} className="error-campo">{error}</small>
        : campo.ayuda && <small id={idAyuda} className="texto-suave">{campo.ayuda}</small>}
    </label>
  )
}

export default function FormularioSolicitud() {
  const [datos, setDatos] = useState({})
  const [privacidad, setPrivacidad] = useState(false)
  const [trampa, setTrampa] = useState('')
  const [errores, setErrores] = useState({})
  const [enviando, setEnviando] = useState(false)
  const [errorEnvio, setErrorEnvio] = useState(null)
  const [referencia, setReferencia] = useState(null)
  const formulario = useRef(null)
  const enCurso = useRef(false) // el estado tarda un render: esto frena también el doble clic inmediato

  function cambiar(e) {
    const { name, value } = e.target
    setDatos((d) => ({ ...d, [name]: value }))
    setErrores((actuales) => ({ ...actuales, [name]: undefined }))
  }

  async function enviar(e) {
    e.preventDefault()
    if (enCurso.current) return
    const encontrados = validar(datos, privacidad)
    setErrores(encontrados)
    setErrorEnvio(null)
    const primero = Object.keys(encontrados)[0]
    if (primero) {
      formulario.current?.querySelector(`[name="${primero}"]`)?.focus()
      return
    }
    enCurso.current = true
    setEnviando(true)
    try {
      const limpios = Object.fromEntries(Object.entries(datos).map(([k, v]) => [k, v.trim()]))
      const r = await api.post('/api/publico/solicitudes-alta', { ...limpios, privacidadLeida: true, sitioWeb: trampa })
      setReferencia(r.referencia)
    } catch (err) {
      // Los datos escritos se conservan para poder reintentar
      setErrorEnvio(err.message)
    } finally {
      enCurso.current = false
      setEnviando(false)
    }
  }

  if (referencia) {
    return (
      <div className="caja-ok" role="status">
        <h3>Solicitud recibida</h3>
        <p>Referencia: <strong>{referencia}</strong>. Guárdala para cualquier consulta.</p>
        <p>La solicitud queda pendiente de revisión por una persona de la distribuidora. Enviarla no da de alta la
          clínica ni activa ninguna cuenta. Si se aprueba, recibirás las instrucciones para activar el acceso.</p>
      </div>
    )
  }

  return (
    <form ref={formulario} onSubmit={enviar} noValidate className="pub-formulario">
      <p className="texto-suave">Los campos marcados con * son obligatorios. No pedimos contraseñas ni documentos.</p>

      <fieldset>
        <legend>Datos de la clínica</legend>
        <div className="pub-campos">
          {CLINICA.map((c) => <Campo key={c.nombre} campo={c} valor={datos[c.nombre] || ''} error={errores[c.nombre]} onChange={cambiar} />)}
        </div>
      </fieldset>

      <fieldset>
        <legend>Veterinario responsable</legend>
        <div className="pub-campos">
          {VETERINARIO.map((c) => <Campo key={c.nombre} campo={c} valor={datos[c.nombre] || ''} error={errores[c.nombre]} onChange={cambiar} />)}
        </div>
      </fieldset>

      <label className="campo" htmlFor="solicitud-observaciones">
        <span>Observaciones <em className="pub-opcional">(opcional)</em></span>
        <textarea id="solicitud-observaciones" name="observaciones" className="input" rows="3" maxLength={MAX_OBSERVACIONES}
          value={datos.observaciones || ''} onChange={cambiar} />
      </label>

      {/* Campo trampa: oculto para las personas; si llega relleno, lo ha escrito un programa */}
      <div className="pub-trampa" aria-hidden="true">
        <label>Sitio web <input tabIndex="-1" autoComplete="off" value={trampa} onChange={(e) => setTrampa(e.target.value)} /></label>
      </div>

      <label className="pub-privacidad" htmlFor="solicitud-privacidad">
        <input id="solicitud-privacidad" name="privacidad" type="checkbox" checked={privacidad}
          aria-invalid={errores.privacidad ? 'true' : undefined}
          onChange={(e) => { setPrivacidad(e.target.checked); setErrores((a) => ({ ...a, privacidad: undefined })) }} />
        <span>He leído la <a href="#privacidad">información de privacidad</a> del formulario. <abbr title="obligatorio">*</abbr></span>
      </label>
      {errores.privacidad && <p className="error-campo">{errores.privacidad}</p>}

      {errorEnvio && <p className="caja-error" role="alert">{errorEnvio} Tus datos siguen en el formulario.</p>}

      <button className="btn btn-primario btn-grande" type="submit" disabled={enviando}>
        {enviando ? 'Enviando…' : 'Enviar solicitud'}
      </button>
    </form>
  )
}
