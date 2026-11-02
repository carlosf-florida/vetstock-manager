import { useEffect, useState } from 'react'
import { api, descargarArchivo } from '../../api.js'
import { fecha } from '../../format.js'
import { ErrorBox, SinDatos, Spinner } from '../Spinner.jsx'

const TIPOS_DOC = ['PEDIDO', 'FACTURA', 'ALBARAN']

/**
 * Documentos del pedido: listado + descarga (todos los roles con permiso)
 * y subida de PDF (solo ADMIN, hasta 10 MB).
 */
export default function DocumentosSeccion({ pedidoId, puedeSubir = false, contrato = null }) {
  const [documentos, setDocumentos] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [errorSubida, setErrorSubida] = useState(null)
  const [ok, setOk] = useState(null)
  const [subiendo, setSubiendo] = useState(false)
  const [tipo, setTipo] = useState('PEDIDO')
  const [archivo, setArchivo] = useState(null)

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const datos = await api.get(`/api/pedidos/${pedidoId}/documentos`)
      setDocumentos(Array.isArray(datos) ? datos : [])
    } catch (err) {
      setError(err.message)
      setDocumentos(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargar() }, [pedidoId])

  function validarArchivo(f) {
    if (!f) return 'Elige un archivo PDF.'
    if (f.type !== 'application/pdf' && !f.name.toLowerCase().endsWith('.pdf')) {
      return 'El documento debe ser un archivo PDF.'
    }
    if (f.size > 10 * 1024 * 1024) {
      return 'El archivo supera el límite de 10 MB.'
    }
    return null
  }

  async function subir(e) {
    e.preventDefault()
    const fallo = validarArchivo(archivo)
    if (fallo) { setErrorSubida(fallo); return }
    setErrorSubida(null)
    setSubiendo(true)
    try {
      const datos = new FormData()
      datos.append('archivo', archivo)
      datos.append('tipo', tipo)
      await api.postForm(`/api/pedidos/${pedidoId}/documentos`, datos)
      setArchivo(null)
      setOk('Documento subido correctamente.')
      setTimeout(() => setOk(null), 3000)
      cargar()
    } catch (err) {
      setErrorSubida(err.message)
    } finally {
      setSubiendo(false)
    }
  }

  async function descargar(doc) {
    try {
      await descargarArchivo(`/api/documentos/${doc.id}/descargar`, doc.nombreArchivo || `documento-${doc.id}.pdf`)
    } catch (err) {
      setError(`No se ha podido descargar el documento: ${err.message}`)
    }
  }

  return (
    <div>
      <ErrorBox mensaje={error} onReintentar={cargar} />
      {ok && <div className="caja-ok"><span aria-hidden="true">✔️</span> {ok}</div>}

      {cargando ? (
        <Spinner texto="Cargando documentos…" />
      ) : !documentos || documentos.length === 0 ? (
        <SinDatos mensaje="Este pedido no tiene documentos todavía." />
      ) : (
        <div className="tabla-scroll">
          <table className="tabla">
            <thead>
              <tr><th>Tipo</th><th>Archivo</th><th>Fecha</th><th></th></tr>
            </thead>
            <tbody>
              {documentos.map((d) => (
                <tr key={d.id}>
                  <td><span className="chip">{d.tipo || 'DOCUMENTO'}</span></td>
                  <td>{d.nombreArchivo || d.nombre || `documento-${d.id}.pdf`}</td>
                  <td>{fecha(d.fecha || d.fechaSubida)}</td>
                  <td>
                    <button type="button" className="btn btn-mini btn-secundario" onClick={() => descargar(d)}>
                      ⬇️ Descargar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {puedeSubir && (
        <form className="tarjeta formulario-doc" onSubmit={subir}>
          <h3>Subir documento (PDF, máx. 10 MB)</h3>
          <div className="campo-doble">
            <label className="campo">
              <span>Tipo de documento</span>
              <select className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}>
                {TIPOS_DOC.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </label>
            <label className="campo">
              <span>Archivo PDF</span>
              <input
                className="input"
                type="file"
                accept="application/pdf,.pdf"
                onChange={(e) => setArchivo(e.target.files?.[0] || null)}
              />
            </label>
          </div>
          {errorSubida && <p className="error-campo">{errorSubida}</p>}
          <div className="acciones-fila">
            <button type="submit" className="btn btn-primario" disabled={subiendo}>
              {subiendo ? 'Subiendo…' : 'Subir documento'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
