import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api.js'
import { dinero, estimarImportesPedido } from '../format.js'
import {
  EVENTO_CARRITO, leerCarrito, vaciarCarrito, cambiarCantidad, quitarDelCarrito,
  maximoPermitido, sincronizarConCatalogo, unidadesCarrito,
} from '../carrito.js'
import { ErrorBox, SinDatos, Spinner } from '../components/Spinner.jsx'
import { useNotificaciones } from '../components/Notificaciones.jsx'
import ImportesPedido from '../components/pedido/ImportesPedido.jsx'
import { useAuth } from '../auth.jsx'

export default function Carrito() {
  const navigate = useNavigate()
  const [lineas, setLineas] = useState(leerCarrito)
  const [observaciones, setObservaciones] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const { usuario } = useAuth()
  const esAdmin = usuario?.rol === 'ADMIN'
  const [clinicas, setClinicas] = useState([])
  const [veterinarios, setVeterinarios] = useState([])
  const [clinicaId, setClinicaId] = useState('')
  const [veterinarioId, setVeterinarioId] = useState('')
  const [comprobando, setComprobando] = useState(true)
  const [errorComprobacion, setErrorComprobacion] = useState(null)
  const [avisos, setAvisos] = useState([])
  const { notificar } = useNotificaciones()

  // Al abrir el carrito se comparan sus líneas con el catálogo actual (stock, precio, productos retirados).
  // Es solo una ayuda: el servidor vuelve a comprobarlo todo al crear el pedido.
  async function comprobarCatalogo() {
    setComprobando(true)
    setErrorComprobacion(null)
    try {
      const productos = await api.get('/api/productos')
      setAvisos(sincronizarConCatalogo(Array.isArray(productos) ? productos : []))
    } catch (err) {
      setErrorComprobacion('No se ha podido comprobar el stock actual: ' + err.message)
    } finally {
      setComprobando(false)
    }
  }

  useEffect(() => {
    setLineas(leerCarrito())
    if (leerCarrito().length > 0) comprobarCatalogo()
    else setComprobando(false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario?.username])

  useEffect(() => {
    if (!esAdmin) return
    Promise.all([api.get('/api/clinicas'), api.get('/api/veterinarios')])
      .then(([c, v]) => { setClinicas(Array.isArray(c) ? c : []); setVeterinarios(Array.isArray(v) ? v : []) })
      .catch((err) => setError('No se pudieron cargar clínicas y veterinarios: ' + err.message))
  }, [esAdmin])

  useEffect(() => {
    function refrescar() { setLineas(leerCarrito()) }
    window.addEventListener(EVENTO_CARRITO, refrescar)
    return () => window.removeEventListener(EVENTO_CARRITO, refrescar)
  }, [])

  function cambiar(linea, valor) {
    const cantidad = Number(valor)
    if (!Number.isInteger(cantidad) || cantidad < 1) return // se ignora mientras se escribe un valor no válido
    if (cantidad > maximoPermitido(linea)) {
      notificar({ tipo: 'error', texto: `De «${linea.nombre}» hay ${linea.stockDisponible} disponibles.` })
      return
    }
    if (!cambiarCantidad(linea.productoId, cantidad)) {
      notificar({ tipo: 'error', texto: 'No se ha podido guardar el cambio en este navegador.' })
    }
  }

  function quitar(linea) {
    if (quitarDelCarrito(linea.productoId)) {
      setAvisos((actuales) => actuales.filter((a) => a.productoId !== linea.productoId))
      notificar({ texto: `Se ha quitado «${linea.nombre}» del carrito.` })
    }
  }

  // Mientras haya productos retirados o más unidades que stock, no se deja crear el pedido
  const hayProblemas = lineas.some((l) => l.noDisponible || l.cantidad > maximoPermitido(l))
  const unidades = unidadesCarrito(lineas)

  // Los avisos de stock o de producto retirado desaparecen en cuanto se corrige la línea
  const avisosVigentes = avisos.filter((a) => {
    const linea = lineas.find((l) => l.productoId === a.productoId)
    if (!linea) return false
    if (a.tipo === 'stock') return linea.cantidad > maximoPermitido(linea)
    return true
  })

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
    if (observaciones.length > 255) errs.observaciones = 'Las observaciones no pueden superar 255 caracteres.'
    if (esAdmin && !clinicaId) errs.clinicaId = 'Elige la clínica para la que creas el pedido.'
    if (esAdmin && !veterinarioId) errs.veterinarioId = 'Elige el veterinario que deberá firmarlo.'
    setErrores(errs)
    return Object.keys(errs).length === 0
  }

  async function crearPedido() {
    const cuerpo = {
      observaciones: observaciones.trim() || null,
      lineas: lineas.map((l) => ({ productoId: l.productoId, cantidad: l.cantidad })),
    }
    if (esAdmin) {
      cuerpo.clinicaId = Number(clinicaId)
      cuerpo.veterinarioId = Number(veterinarioId)
    }
    return api.post('/api/pedidos', cuerpo)
  }

  // Administración ve el pedido creado en su propio panel
  const rutaPedido = (id) => (esAdmin ? `/admin/pedidos/${id}` : `/pedidos/${id}`)

  function elegirClinica(id) {
    setClinicaId(id)
    // Por defecto se propone el veterinario responsable de la clínica
    const clinica = clinicas.find((c) => String(c.id) === String(id))
    setVeterinarioId(clinica?.veterinarioResponsableId ? String(clinica.veterinarioResponsableId) : '')
  }

  async function guardarBorrador() {
    if (!validar()) return
    setEnviando(true)
    setError(null)
    try {
      const pedido = await crearPedido()
      vaciarCarrito()
      navigate(`${rutaPedido(pedido.id)}?creado=borrador`)
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
        navigate(`${rutaPedido(pedido.id)}?creado=firma`)
      } catch (errEnvio) {
        // El pedido quedó como BORRADOR; lo dejamos accesible y avisamos.
        vaciarCarrito()
        navigate(`${rutaPedido(pedido.id)}?creado=borrador&errorEnvio=${encodeURIComponent(errEnvio.message)}`)
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
        <SinDatos mensaje="Tu carrito está vacío. Añade productos desde el catálogo y aparecerán aquí." />
        <p className="acciones-fila">
          <Link className="btn btn-primario" to="/catalogo">Ir al catálogo</Link>
          {!esAdmin && <Link className="btn btn-secundario" to="/mis-pedidos">Ver mis pedidos</Link>}
        </p>
      </section>
    )
  }

  return (
    <section>
      <h1>Tu carrito</h1>
      <p className="texto-suave">
        {unidades} {unidades === 1 ? 'unidad' : 'unidades'} de {lineas.length} {lineas.length === 1 ? 'producto' : 'productos'}.
        Revisa las cantidades antes de guardar el pedido como borrador o enviarlo a firma.
      </p>

      {comprobando && <Spinner texto="Comprobando stock y precios actuales…" />}
      <ErrorBox mensaje={errorComprobacion} onReintentar={comprobarCatalogo} />
      {avisosVigentes.length > 0 && (
        <div className="caja-error" role="status">
          <span aria-hidden="true">⚠️</span>
          <ul className="caja-error-texto lista-avisos">
            {avisosVigentes.map((a) => <li key={`${a.productoId}-${a.tipo}`}>{a.texto}</li>)}
          </ul>
        </div>
      )}
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
              <tr key={l.productoId} className={l.noDisponible || l.cantidad > maximoPermitido(l) ? 'fila-aviso' : undefined}>
                <td>
                  {l.nombre}
                  {l.noDisponible
                    ? <small className="error-campo">No disponible</small>
                    : Number.isFinite(maximoPermitido(l)) && <small className="texto-suave">{l.stockDisponible} disponibles</small>}
                </td>
                <td>{l.codigo}</td>
                <td>{dinero(l.precio)}</td>
                <td>
                  <div className="cantidad-zona">
                    <button type="button" className="btn btn-mini" aria-label={`Quitar una unidad de ${l.nombre}`}
                      onClick={() => cambiar(l, l.cantidad - 1)} disabled={enviando || l.cantidad <= 1}>−</button>
                    <input
                      className="input input-cantidad"
                      type="number"
                      min="1"
                      max={Number.isFinite(maximoPermitido(l)) ? maximoPermitido(l) : undefined}
                      step="1"
                      value={l.cantidad}
                      onChange={(e) => cambiar(l, e.target.value)}
                      disabled={enviando}
                      aria-label={`Cantidad de ${l.nombre}`}
                    />
                    <button type="button" className="btn btn-mini" aria-label={`Añadir una unidad de ${l.nombre}`}
                      onClick={() => cambiar(l, l.cantidad + 1)} disabled={enviando || l.cantidad >= maximoPermitido(l)}>+</button>
                  </div>
                </td>
                <td>{dinero(Number(l.precio || 0) * Number(l.cantidad || 0))}</td>
                <td>
                  <button type="button" className="btn btn-peligro btn-mini" onClick={() => quitar(l)} disabled={enviando} aria-label={`Quitar ${l.nombre} del carrito`}>
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

        {esAdmin ? (
          <>
            <div className="aviso aviso-info">
              <strong>Pedido en nombre de una clínica:</strong>{' '}
              quedará registrado que lo ha creado administración. Solo el veterinario elegido podrá firmarlo y la clínica será quien pague.
            </div>
            <div className="campo-doble">
              <label className="campo">
                <span>Clínica *</span>
                <select className="input" value={clinicaId} onChange={(e) => elegirClinica(e.target.value)}>
                  <option value="">— Elige una clínica —</option>
                  {clinicas.filter((c) => c.activa !== false).map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
                </select>
                {errores.clinicaId && <small className="error-campo">{errores.clinicaId}</small>}
              </label>
              <label className="campo">
                <span>Veterinario firmante *</span>
                <select className="input" value={veterinarioId} onChange={(e) => setVeterinarioId(e.target.value)} disabled={!clinicaId}>
                  <option value="">— Elige un veterinario —</option>
                  {veterinarios.filter((v) => String(v.clinicaId) === String(clinicaId)).map((v) => (
                    <option key={v.id} value={v.id}>{v.nombreCompleto || v.username}</option>
                  ))}
                </select>
                {errores.veterinarioId && <small className="error-campo">{errores.veterinarioId}</small>}
              </label>
            </div>
          </>
        ) : (
          <div className="aviso aviso-info">
            <strong>Responsable automático por clínica:</strong>{' '}
            el pedido toma el veterinario responsable asignado a tu clínica. Cuando esté pendiente de firma, solo ese veterinario puede firmarlo con su cuenta.
          </div>
        )}

        <label className="campo">
          <span>Observaciones</span>
          <textarea
            className="input"
            rows="3"
            maxLength={255}
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            placeholder="Indicaciones para la preparación o entrega (opcional)"
          />
          {errores.observaciones && <small className="error-campo">{errores.observaciones}</small>}
        </label>

        {hayProblemas && (
          <p className="error-campo">Corrige los productos marcados (sin stock suficiente o retirados) para poder continuar.</p>
        )}
        <div className="acciones-fila">
          <button type="button" className="btn btn-primario btn-grande" onClick={enviarAFirma} disabled={enviando || hayProblemas}>
            {enviando ? 'Enviando…' : 'Enviar a firma'}
          </button>
          <button type="button" className="btn btn-secundario" onClick={guardarBorrador} disabled={enviando || hayProblemas}>
            {enviando ? 'Guardando…' : 'Guardar como borrador'}
          </button>
          <button
            type="button"
            className="btn btn-peligro"
            onClick={() => { if (window.confirm('¿Vaciar el carrito? Se quitarán todos los productos.')) vaciarCarrito() }}
            disabled={enviando}
          >
            Vaciar carrito
          </button>
        </div>
        <p className="texto-suave">
          «Enviar a firma» reserva el stock y deja el pedido pendiente de la firma del veterinario asignado; «Guardar como borrador» lo deja editable sin reservar stock.
        </p>
        {enviando && <p className="texto-suave">Creando el pedido en el servidor…</p>}
      </div>
    </section>
  )
}
