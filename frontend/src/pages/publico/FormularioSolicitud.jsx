import { useEffect, useRef, useState } from 'react'
import { api } from '../../api.js'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const TELEFONO = /^[+0-9 ()-]{6,20}$/
const CODIGO_POSTAL = /^[A-Za-z0-9 -]{3,10}$/

// Mismo límite que el servidor (vetstock.solicitudes.documento-max-mb). El servidor vuelve a comprobarlo todo.
const MAX_MB = 5
const MIN_LADO_PX = 200
const TIPOS_IMAGEN = { 'image/jpeg': ['jpg', 'jpeg'], 'image/png': ['png'] }

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
const PASOS = [
  { titulo: 'Clínica', encabezado: 'Datos de la clínica' },
  { titulo: 'Veterinario', encabezado: 'Veterinario responsable y acreditación' },
  { titulo: 'Revisión', encabezado: 'Revisión y envío' },
]
const MAX_OBSERVACIONES = 500

function validarCampos(campos, datos) {
  const errores = {}
  for (const c of campos) {
    const valor = (datos[c.nombre] || '').trim()
    if (c.obligatorio && !valor) errores[c.nombre] = 'Este campo es obligatorio.'
    else if (valor && c.formato && !c.formato.test(valor)) errores[c.nombre] = 'El formato no es válido.'
  }
  return errores
}

function formatoTamano(bytes) {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
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

/** Tres círculos conectados. El estado se dice con texto, no solo con color. */
function IndicadorPasos({ paso, visitado, onIr }) {
  return (
    <div className="pasos">
      <ol className="pasos-lista">
        {PASOS.map((p, i) => {
          const numero = i + 1
          const estado = numero === paso ? 'actual' : numero < paso || numero <= visitado ? 'completado' : 'pendiente'
          const textoEstado = { actual: 'paso actual', completado: 'completado', pendiente: 'pendiente' }[estado]
          const contenido = (
            <>
              <span className="pasos-circulo" aria-hidden="true">{estado === 'completado' ? '✓' : numero}</span>
              <span className="pasos-texto">{p.titulo}</span>
              <span className="solo-lector"> ({textoEstado})</span>
            </>
          )
          return (
            <li key={p.titulo} className={`pasos-item pasos-${estado}`} aria-current={estado === 'actual' ? 'step' : undefined}>
              {numero !== paso && numero <= visitado
                ? <button type="button" className="pasos-boton" onClick={() => onIr(numero)}>{contenido}</button>
                : <span className="pasos-boton">{contenido}</span>}
            </li>
          )
        })}
      </ol>
      <p className="pasos-contador">Paso {paso} de {PASOS.length}</p>
    </div>
  )
}

function Documento({ documento, error, onElegir, onRetirar }) {
  const entrada = useRef(null)
  return (
    <fieldset className="pub-documento">
      <legend>Documento que acredita la responsabilidad del veterinario <abbr title="obligatorio">*</abbr></legend>
      <p id="documento-ayuda">
        Adjunta una imagen legible de un documento que identifique a la clínica y al veterinario y acredite su relación
        como responsable. El equipo revisará la documentación antes de aprobar el alta.
      </p>
      <ul className="pub-aclaraciones texto-suave">
        <li>No es una fotografía del veterinario ni la firma de un pedido.</li>
        <li>No es una comprobación automática de la colegiación: lo revisa una persona.</li>
        <li>No envíes el DNI ni documentos que no sirvan para esta finalidad.</li>
        <li>Formatos JPG o PNG, hasta {MAX_MB} MB. Si recargas la página, tendrás que volver a adjuntarla.</li>
      </ul>
      <input ref={entrada} id="solicitud-documento" name="documento" type="file" accept="image/jpeg,image/png"
        className="pub-archivo-oculto" aria-describedby="documento-ayuda" aria-invalid={error ? 'true' : undefined}
        onChange={(e) => { onElegir(e.target.files?.[0]); e.target.value = '' }} />
      {documento ? (
        <div className="pub-documento-elegido">
          <img src={documento.url} alt={`Vista previa de ${documento.archivo.name}`} className="pub-documento-vista" />
          <div>
            <p className="pub-documento-nombre"><strong>{documento.archivo.name}</strong> · {formatoTamano(documento.archivo.size)}</p>
            <p className="pub-acciones-documento">
              <button type="button" className="btn btn-secundario" onClick={() => entrada.current?.click()}>Sustituir imagen</button>
              <button type="button" className="btn btn-secundario" onClick={onRetirar}>Retirar</button>
            </p>
          </div>
        </div>
      ) : (
        <button type="button" className="btn btn-secundario" onClick={() => entrada.current?.click()}>Elegir imagen…</button>
      )}
      {error && <p className="error-campo" role="alert">{error}</p>}
    </fieldset>
  )
}

function Resumen({ titulo, campos, datos, onEditar }) {
  return (
    <section className="pub-resumen">
      <div className="pub-resumen-cabecera">
        <h4>{titulo}</h4>
        <button type="button" className="btn btn-secundario btn-mini" onClick={onEditar}>Editar</button>
      </div>
      <dl>
        {campos.map((c) => (
          <div key={c.nombre}>
            <dt>{c.etiqueta}</dt>
            <dd>{(datos[c.nombre] || '').trim() || <span className="texto-suave">—</span>}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

export default function FormularioSolicitud() {
  const [datos, setDatos] = useState({})
  const [paso, setPaso] = useState(1)
  const [visitado, setVisitado] = useState(1)
  const [documento, setDocumento] = useState(null)   // { archivo, url }
  const [errorDocumento, setErrorDocumento] = useState(null)
  const [privacidad, setPrivacidad] = useState(false)
  const [trampa, setTrampa] = useState('')
  const [errores, setErrores] = useState({})
  const [enviando, setEnviando] = useState(false)
  const [errorEnvio, setErrorEnvio] = useState(null)
  const [referencia, setReferencia] = useState(null)
  const formulario = useRef(null)
  const encabezado = useRef(null)
  const enCurso = useRef(false) // el estado tarda un render: esto frena también el doble clic inmediato
  const primerRender = useRef(true)

  // La vista previa usa una URL temporal del navegador: se libera al cambiar de imagen o salir
  useEffect(() => () => { if (documento) URL.revokeObjectURL(documento.url) }, [documento])

  // Al cambiar de paso, el foco va a su título (útil con teclado y lector de pantalla)
  useEffect(() => {
    if (primerRender.current) { primerRender.current = false; return }
    encabezado.current?.focus()
  }, [paso])

  function cambiar(e) {
    const { name, value } = e.target
    setDatos((d) => ({ ...d, [name]: value }))
    setErrores((actuales) => ({ ...actuales, [name]: undefined }))
  }

  function elegirArchivo(archivo) {
    if (!archivo) return
    const extension = archivo.name.split('.').pop().toLowerCase()
    if (!TIPOS_IMAGEN[archivo.type]?.includes(extension)) {
      setErrorDocumento('El archivo debe ser una imagen JPG o PNG.')
      return
    }
    if (archivo.size > MAX_MB * 1024 * 1024) {
      setErrorDocumento(`La imagen supera los ${MAX_MB} MB permitidos.`)
      return
    }
    const url = URL.createObjectURL(archivo)
    const imagen = new Image()
    imagen.onload = () => {
      if (imagen.naturalWidth < MIN_LADO_PX || imagen.naturalHeight < MIN_LADO_PX) {
        URL.revokeObjectURL(url)
        setErrorDocumento(`La imagen es demasiado pequeña para leerse (mínimo ${MIN_LADO_PX} píxeles por lado).`)
        return
      }
      setErrorDocumento(null)
      setDocumento({ archivo, url })
    }
    imagen.onerror = () => {
      URL.revokeObjectURL(url)
      setErrorDocumento('La imagen está dañada o no se puede abrir.')
    }
    imagen.src = url
  }

  function erroresDelPaso(n) {
    if (n === 1) return validarCampos(CLINICA, datos)
    if (n === 2) {
      const e = validarCampos(VETERINARIO, datos)
      if (!documento) e.documento = 'Adjunta el documento acreditativo.'
      return e
    }
    return privacidad ? {} : { privacidad: 'Confirma que has leído la información de privacidad.' }
  }

  /** Muestra los errores del paso y lleva el foco al primero. Devuelve true si el paso es correcto. */
  function comprobarPaso(n) {
    const encontrados = erroresDelPaso(n)
    setErrores((actuales) => ({ ...actuales, ...encontrados }))
    if (encontrados.documento) setErrorDocumento(encontrados.documento)
    const primero = Object.keys(encontrados)[0]
    if (primero) {
      setTimeout(() => formulario.current?.querySelector(primero === 'documento' ? '.pub-documento button' : `[name="${primero}"]`)?.focus())
      return false
    }
    return true
  }

  /** Volver siempre se puede; avanzar exige que los pasos intermedios sean correctos. */
  function irA(destino) {
    for (let n = paso; n < destino; n++) {
      if (!comprobarPaso(n)) {
        setPaso(n)
        return
      }
    }
    setPaso(destino)
    setVisitado((v) => Math.max(v, destino))
  }

  async function alEnviar(e) {
    e.preventDefault()
    // Enter en un paso intermedio equivale a «Siguiente»: nunca envía saltándose la revisión
    if (paso < PASOS.length) {
      irA(paso + 1)
      return
    }
    if (enCurso.current) return
    for (let n = 1; n <= PASOS.length; n++) {
      if (!comprobarPaso(n)) {
        setPaso(n)
        return
      }
    }
    setErrorEnvio(null)
    enCurso.current = true
    setEnviando(true)
    try {
      const limpios = Object.fromEntries(Object.entries(datos).map(([k, v]) => [k, v.trim()]))
      const cuerpo = new FormData()
      cuerpo.append('datos', new Blob([JSON.stringify({ ...limpios, privacidadLeida: true, sitioWeb: trampa })], { type: 'application/json' }))
      cuerpo.append('documento', documento.archivo)
      const r = await api.postForm('/api/publico/solicitudes-alta', cuerpo)
      setReferencia(r.referencia)
    } catch (err) {
      // Los datos y la imagen se conservan para poder reintentar
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
        <p>La solicitud y el documento quedan pendientes de revisión por una persona de la distribuidora. Enviarla no da de
          alta la clínica ni activa ninguna cuenta. Si se aprueba, recibirás las instrucciones para activar el acceso.</p>
      </div>
    )
  }

  return (
    <form ref={formulario} onSubmit={alEnviar} noValidate className="pub-formulario">
      <IndicadorPasos paso={paso} visitado={visitado} onIr={irA} />
      <h3 ref={encabezado} tabIndex="-1" className="pub-paso-titulo">{PASOS[paso - 1].encabezado}</h3>

      {paso === 1 && (
        <fieldset>
          <legend className="solo-lector">Datos de la clínica</legend>
          <p className="texto-suave">Los campos marcados con * son obligatorios. No pedimos contraseñas.</p>
          <div className="pub-campos">
            {CLINICA.map((c) => <Campo key={c.nombre} campo={c} valor={datos[c.nombre] || ''} error={errores[c.nombre]} onChange={cambiar} />)}
          </div>
        </fieldset>
      )}

      {paso === 2 && (
        <>
          <fieldset>
            <legend>Veterinario responsable</legend>
            <div className="pub-campos">
              {VETERINARIO.map((c) => <Campo key={c.nombre} campo={c} valor={datos[c.nombre] || ''} error={errores[c.nombre]} onChange={cambiar} />)}
            </div>
          </fieldset>
          <Documento documento={documento} error={errorDocumento} onElegir={elegirArchivo}
            onRetirar={() => { setDocumento(null); setErrorDocumento(null) }} />
        </>
      )}

      {paso === 3 && (
        <>
          <Resumen titulo="Clínica" campos={CLINICA} datos={datos} onEditar={() => irA(1)} />
          <Resumen titulo="Veterinario responsable" campos={VETERINARIO} datos={datos} onEditar={() => irA(2)} />
          <section className="pub-resumen">
            <div className="pub-resumen-cabecera">
              <h4>Documento acreditativo</h4>
              <button type="button" className="btn btn-secundario btn-mini" onClick={() => irA(2)}>Cambiar</button>
            </div>
            {documento && (
              <div className="pub-documento-elegido">
                <img src={documento.url} alt={`Vista previa de ${documento.archivo.name}`} className="pub-documento-vista" />
                <p className="pub-documento-nombre"><strong>{documento.archivo.name}</strong> · {formatoTamano(documento.archivo.size)}</p>
              </div>
            )}
          </section>

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
            <span>He leído la <a href="#privacidad">información de privacidad</a> del formulario, que incluye el uso del documento adjunto. <abbr title="obligatorio">*</abbr></span>
          </label>
          {errores.privacidad && <p className="error-campo">{errores.privacidad}</p>}
          {errorEnvio && <p className="caja-error" role="alert">{errorEnvio} Tus datos y la imagen siguen en el formulario.</p>}
        </>
      )}

      <div className="pub-navegacion">
        {paso > 1 && <button type="button" className="btn btn-secundario btn-grande" onClick={() => irA(paso - 1)} disabled={enviando}>Anterior</button>}
        {paso < PASOS.length
          ? <button type="button" className="btn btn-primario btn-grande" onClick={() => irA(paso + 1)}>Siguiente</button>
          : <button type="submit" className="btn btn-primario btn-grande" disabled={enviando}>{enviando ? 'Enviando…' : 'Enviar solicitud'}</button>}
      </div>
    </form>
  )
}
