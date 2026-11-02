import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api.js'
import { dinero, estimarImportesPedido } from '../format.js'
import { leerCarrito, guardarCarrito, vaciarCarrito } from '../carrito.js'
import { ErrorBox, SinDatos } from '../components/Spinner.jsx'
import ImportesPedido from '../components/pedido/ImportesPedido.jsx'

export default function Carrito() {
  const navigate = useNavigate()
  const [lineas, setLineas] = useState(leerCarrito)
  const [observaciones, setObservaciones] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    function refrescar() { setLineas(leerCarrito()) }
    window.addEventListener('vetstock-carrito', refrescar)
    return () => window.removeEventListener('vetstock-carrito', refrescar)
  }, [])

  function cambiarCantidad(id, valor) {
    const cantidad = Number(valor)
    const nuevas = lineas.map((l) =>
      l.productoId === id ? { ...l, cantidad: Number.isInteger(cantidad) && cantidad > 0 ? cantidad : l.cantidad } : l,
    )
    setLineas(nuevas)
    guardarCarrito(nuevas)
  }

  function quitar(id) {
    const nuevas = lineas.filter((l) => l.productoId !== id)
    setLineas(nuevas)
    guardarCarrito(nuevas)
  }

  const importesEstimados = estimarImportesPedido(lineas)

  function validar() {
    const errs = {}
    if (lineas.length === 0) errs.general = 'El carrito está vacío.'
    for (const l of lineas) {
      if (!Number.isInteger(l.cantidad) || l.cantidad < 1) {
        errs.general = 'Todas las cantidades deben ser números enteros de 1 o más. Revisa las líneas.'
        break
      }
    }
    if (observaciones.length > 2000) errs.observaciones = 'Las observaciones no pueden superar 2000 caracteres.'
    setErrores(errs)
    return Object.keys(errs).length === 0
  }

  async function crearPedido() {
    const cuerpo = {
      observaciones: observaciones.trim() || null,
      lineas: lineas.map((l) => ({ productoId: l.productoId, cantidad: l.cantidad })),
    }
    return api.post('/api/pedidos', cuerpo)
  }

  async function guardarBorrador() {
    if (!validar()) return
    setEnviando(true)
    setError(null)
    try {
      const pedido = await crearPedido()
      vaciarCarrito()
      navigate(`/pedidos/${pedido.id}?creado=borrador`)
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) setError('Tu sesión ha caducado. Vuelve a iniciar sesión.')
      else setError(err.message)
      setEnviando(false)
    }
  }

  async function enviarAFirma() {
    if (!validar()) return
    setEnviando(true)
    setError(null)
    try {
      const pedido = await crearPedido()
      try {
        await api.patch(`/api/pedidos/${pedido.id}/enviar-firma`)
        vaciarCarrito()
        navigate(`/pedidos/${pedido.id}?creado=firma`)
      } catch (errEnvio) {
        // El pedido quedó como BORRADOR; lo dejamos accesible y avisamos.
        vaciarCarrito()
        navigate(`/pedidos/${pedido.id}?creado=borrador&errorEnvio=${encodeURIComponent(errEnvio.message)}`)
      }
    } catch (err) {
      setError(err.message)
      setEnviando(false)
    }
  }

  if (lineas.length === 0) {
    return (
      <section>
        <h1>Tu carrito</h1>
        <SinDatos mensaje="Tu carrito está vacío. Ve al catálogo para añadir productos." />
        <p><Link className="btn btn-primario" to="/catalogo">Ir al catálogo</Link></p>
      </section>
    )
  }

  return (
    <section>
      <h1>Tu carrito</h1>
      <p className="texto-suave">Revisa las cantidades antes de guardar el pedido como borrador o enviarlo a firma.</p>

      <ErrorBox mensaje={errores.general || error} />

      <div className="tabla-scroll">
        <table className="tabla">
          <thead>
            <tr>
              <th>Producto</th>
              <th>Código</th>
              <th>Precio sin IVA</th>
              <th>Cantidad</th>
              <th>Subtotal sin IVA</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {lineas.map((l) => (
              <tr key={l.productoId}>
                <td>{l.nombre}</td>
                <td>{l.codigo}</td>
                <td>{dinero(l.precio)}</td>
                <td>
                  <div className="cantidad-zona">
                    <button type="button" className="btn btn-mini" aria-label="Quitar una unidad"
                      onClick={() => cambiarCantidad(l.productoId, l.cantidad - 1)} disabled={enviando}>−</button>
                    <input
                      className="input input-cantidad"
                      type="number"
                      min="1"
                      step="1"
                      value={l.cantidad}
                      onChange={(e) => cambiarCantidad(l.productoId, e.target.value)}
                      disabled={enviando}
                      aria-label={`Cantidad de ${l.nombre}`}
                    />
                    <button type="button" className="btn btn-mini" aria-label="Añadir una unidad"
                      onClick={() => cambiarCantidad(l.productoId, l.cantidad + 1)} disabled={enviando}>+</button>
                  </div>
                </td>
                <td>{dinero(Number(l.precio || 0) * Number(l.cantidad || 0))}</td>
                <td>
                  <button type="button" className="btn btn-peligro btn-mini" onClick={() => quitar(l.productoId)} disabled={enviando}>
                    Quitar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="carrito-pie">
        <div className="carrito-total">
          <p className="texto-suave">Estimación para pedidos nuevos; el precio definitivo lo calcula el servidor.</p>
          <ImportesPedido importes={importesEstimados} />
        </div>
      </div>

      <div className="tarjeta formulario-pedido">
        <h2>Datos del pedido</h2>

        <div className="aviso aviso-info">
          <strong>Responsable automático por clínica:</strong>{' '}
          el pedido toma el veterinario responsable asignado a tu clínica. Cuando esté pendiente de firma, solo ese veterinario puede firmarlo con su cuenta.
        </div>

        <label className="campo">
          <span>Observaciones</span>
          <textarea
            className="input"
            rows="3"
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            placeholder="Indicaciones para la preparación o entrega (opcional)"
          />
          {errores.observaciones && <small className="error-campo">{errores.observaciones}</small>}
        </label>

        <div className="acciones-fila">
          <button type="button" className="btn btn-secundario" onClick={guardarBorrador} disabled={enviando}>
            {enviando ? 'Guardando…' : 'Guardar como borrador'}
          </button>
          <button type="button" className="btn btn-primario" onClick={enviarAFirma} disabled={enviando}>
            {enviando ? 'Enviando…' : 'Enviar a firma'}
          </button>
          <button type="button" className="btn btn-peligro" onClick={() => { vaciarCarrito(); setLineas([]) }} disabled={enviando}>
            Vaciar carrito
          </button>
        </div>
        {enviando && <p className="texto-suave">Creando el pedido en el servidor…</p>}
      </div>
    </section>
  )
}
