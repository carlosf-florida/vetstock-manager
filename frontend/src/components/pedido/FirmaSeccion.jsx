import { useRef, useState } from 'react'
import { api } from '../../api.js'
import { ErrorBox } from '../Spinner.jsx'
import LineasPedido from './LineasPedido.jsx'
import ImportesPedido from './ImportesPedido.jsx'

const TAM_MAX_IMAGEN = 5 * 1024 * 1024 // 5 MB

/**
 * Firma simple por dibujo o imagen PNG/JPG. Antes de firmar se muestra el
 * contenido exacto que se autoriza. La autorización del firmante se valida en
 * el servidor; el número de colegiado solo identifica al responsable.
 */
export default function FirmaSeccion({ pedido, onFirmado }) {
  const canvasRef = useRef(null)
  const dibujando = useRef(false)
  const [metodo, setMetodo] = useState('DIBUJO')
  const [archivo, setArchivo] = useState(null)
  const [numeroColegiado, setNumeroColegiado] = useState('')
  const [errorArchivo, setErrorArchivo] = useState(null)
  const [confirmado, setConfirmado] = useState(false)
  const [error, setError] = useState(null)
  const [firmando, setFirmando] = useState(false)

  function posicion(evento) {
    const canvas = canvasRef.current
    const rect = canvas.getBoundingClientRect()
    return {
      x: ((evento.clientX - rect.left) / rect.width) * canvas.width,
      y: ((evento.clientY - rect.top) / rect.height) * canvas.height,
    }
  }

  function empezarTrazo(e) {
    e.preventDefault()
    const ctx = canvasRef.current.getContext('2d')
    const p = posicion(e)
    dibujando.current = true
    ctx.beginPath()
    ctx.moveTo(p.x, p.y)
  }

  function trazar(e) {
    if (!dibujando.current) return
    e.preventDefault()
    const ctx = canvasRef.current.getContext('2d')
    const p = posicion(e)
    ctx.lineWidth = 2.5
    ctx.lineCap = 'round'
    ctx.strokeStyle = '#0f3a5f'
    ctx.lineTo(p.x, p.y)
    ctx.stroke()
  }

  function acabarTrazo() {
    dibujando.current = false
  }

  function limpiar() {
    const canvas = canvasRef.current
    const ctx = canvas.getContext('2d')
    ctx.clearRect(0, 0, canvas.width, canvas.height)
  }

  function elegirImagen(e) {
    const f = e.target.files?.[0] || null
    setErrorArchivo(null)
    if (!f) { setArchivo(null); return }
    if (!['image/png', 'image/jpeg'].includes(f.type)) {
      setErrorArchivo('La imagen debe ser PNG o JPG.')
      setArchivo(null)
      return
    }
    if (f.size > TAM_MAX_IMAGEN) {
      setErrorArchivo('La imagen supera el límite de 5 MB.')
      setArchivo(null)
      return
    }
    setArchivo(f)
  }

  function aBase64(blob) {
    return new Promise((resolver, rechazar) => {
      const lector = new FileReader()
      lector.onload = () => resolver(lector.result.split(',')[1])
      lector.onerror = () => rechazar(new Error('No se ha podido leer la imagen.'))
      lector.readAsDataURL(blob)
    })
  }

  async function firmar() {
    setError(null)
    if (!confirmado) {
      setError('Debes marcar la casilla de confirmación de autorización.')
      return
    }

    let imagenBase64 = null
    let nombreArchivo = null
    if (metodo === 'DIBUJO') {
      const canvas = canvasRef.current
      if (canvasVacio(canvas)) {
        setError('Dibuja tu firma en el recuadro antes de firmar (o cambia a «Subir imagen»).')
        return
      }
      const blob = await new Promise((resolver) => canvas.toBlob(resolver, 'image/png'))
      if (!blob) {
        setError('No se ha podido preparar la imagen de la firma.')
        return
      }
      imagenBase64 = await aBase64(blob)
    } else {
      if (!archivo) {
        setError('Elige una imagen PNG o JPG de tu firma.')
        return
      }
      imagenBase64 = await aBase64(archivo)
      nombreArchivo = archivo.name
    }

    setFirmando(true)
    try {
      await api.patch(`/api/pedidos/${pedido.id}/firmar`, {
        tipoFirma: metodo,
        imagenBase64,
        nombreArchivo: metodo === 'IMAGEN' ? nombreArchivo : undefined,
        confirmacion: true,
        numeroColegiado: numeroColegiado.trim() || undefined,
      })
      onFirmado?.()
    } catch (err) {
      setError(err.message)
      setFirmando(false)
    }
  }

  function canvasVacio(canvas) {
    const ctx = canvas.getContext('2d')
    const datos = ctx.getImageData(0, 0, canvas.width, canvas.height).data
    for (let i = 3; i < datos.length; i += 4) {
      if (datos[i] !== 0) return false
    }
    return true
  }

  return (
    <div className="tarjeta firma-panel">
      <h3>Firmar el pedido</h3>
      <div className="caja-aviso" role="note">
        La firma registrada aquí no tiene validez legal certificada: es un prototipo académico de firma electrónica simple.
      </div>

      <h4>1. Revisa lo que vas a autorizar</h4>
      <p className="texto-suave">
        Tras firmar, los productos y cantidades quedan bloqueados y se guarda una copia exacta de este contenido.
      </p>
      <LineasPedido pedido={pedido} editable={false} />
      <ImportesPedido pedido={pedido} />

      <h4>2. Firma</h4>

      <label className="campo">
        <span>Nº de colegiado del responsable (opcional)</span>
        <input
          className="input"
          value={numeroColegiado}
          maxLength="20"
          onChange={(e) => setNumeroColegiado(e.target.value)}
          placeholder={pedido.numeroColegiadoResponsable || 'Opcional'}
        />
        <small className="texto-suave">Solo identifica al veterinario responsable; no es un PIN ni un método de autenticación.</small>
      </label>

      <div className="campo">
        <span>Método de firma</span>
        <div className="opciones-firma">
          <label className="campo-radio">
            <input type="radio" name={`metodo-${pedido.id}`} checked={metodo === 'DIBUJO'} onChange={() => setMetodo('DIBUJO')} />
            <span>Dibujar con el dedo o el ratón</span>
          </label>
          <label className="campo-radio">
            <input type="radio" name={`metodo-${pedido.id}`} checked={metodo === 'IMAGEN'} onChange={() => setMetodo('IMAGEN')} />
            <span>Subir imagen (PNG/JPG, máx. 5 MB)</span>
          </label>
        </div>
      </div>

      {metodo === 'DIBUJO' ? (
        <div>
          <canvas
            ref={canvasRef}
            width={800}
            height={300}
            className="firma-canvas"
            aria-label="Recuadro para dibujar la firma"
            onPointerDown={empezarTrazo}
            onPointerMove={trazar}
            onPointerUp={acabarTrazo}
            onPointerLeave={acabarTrazo}
          />
          <div className="acciones-fila">
            <button type="button" className="btn btn-secundario btn-mini" onClick={limpiar}>Limpiar firma</button>
          </div>
        </div>
      ) : (
        <div>
          <label className="campo">
            <span>Imagen de la firma</span>
            <input
              className="input"
              type="file"
              accept="image/png,image/jpeg,.png,.jpg,.jpeg"
              onChange={elegirImagen}
            />
          </label>
          {errorArchivo && <p className="error-campo">{errorArchivo}</p>}
          {archivo && <p className="texto-suave">Imagen elegida: {archivo.name}</p>}
        </div>
      )}

      <label className="campo campo-checkbox">
        <input type="checkbox" checked={confirmado} onChange={(e) => setConfirmado(e.target.checked)} />
        <span>He revisado productos, cantidades e importes y autorizo este pedido con mi propia firma e identidad *</span>
      </label>

      <ErrorBox mensaje={error} />

      <div className="acciones-fila">
        <button type="button" className="btn btn-primario btn-grande" onClick={firmar} disabled={firmando}>
          {firmando ? 'Firmando…' : 'Firmar y continuar al pago'}
        </button>
      </div>
      {firmando && <p className="texto-suave">Enviando la firma al servidor…</p>}
    </div>
  )
}
