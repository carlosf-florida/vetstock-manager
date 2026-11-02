import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { dinero, estimarImportesPedido, fechaCorta } from '../format.js'
import { EVENTO_CARRITO, anadirAlCarrito, leerCarrito, unidadesCarrito } from '../carrito.js'
import { useNotificaciones } from '../components/Notificaciones.jsx'
import { BadgeEstadoPedido } from '../components/Badges.jsx'
import { ErrorBox, Spinner } from '../components/Spinner.jsx'

const MAX_HABITUALES = 4

/**
 * Inicio privado del portal del cliente (veterinarios y auxiliares).
 * Solo muestra datos reales de su clínica: el carrito en curso, los pedidos
 * pendientes (de /api/dashboard) y los productos que más ha pedido la clínica.
 */
export default function Inicio() {
  const { usuario } = useAuth()
  const { notificar } = useNotificaciones()
  const [resumen, setResumen] = useState(null)
  const [habituales, setHabituales] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [carrito, setCarrito] = useState(leerCarrito)

  useEffect(() => {
    const refrescar = () => setCarrito(leerCarrito())
    refrescar()
    window.addEventListener(EVENTO_CARRITO, refrescar)
    return () => window.removeEventListener(EVENTO_CARRITO, refrescar)
  }, [])

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const [datos, pedidos, productos] = await Promise.all([
        api.get('/api/dashboard'),
        api.get('/api/pedidos'),
        api.get('/api/productos'),
      ])
      setResumen(datos)
      setHabituales(productosHabituales(pedidos, productos))
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargar() }, [])

  function anadir(producto) {
    const resultado = anadirAlCarrito(producto, 1)
    if (resultado.ok) {
      notificar({ texto: `Se ha añadido 1 unidad de «${producto.nombre}» al carrito.`, enlace: { texto: 'Ver carrito', a: '/carrito' } })
    } else {
      notificar({ tipo: 'error', texto: resultado.mensaje })
    }
  }

  const unidades = unidadesCarrito(carrito)
  const nombre = (usuario?.nombreCompleto || usuario?.username || '').split(' (')[0]

  return (
    <section className="inicio">
      <header className="inicio-cabecera">
        <div>
          <h1>Hola, {nombre}</h1>
          <p className="texto-suave">
            {usuario?.clinicaNombre ? <>Portal de pedidos de <strong>{usuario.clinicaNombre}</strong>. </> : null}
            Prepara tu pedido, sigue su firma y su pago, y consulta su envío.
          </p>
        </div>
        <Link className="btn btn-primario btn-grande" to="/catalogo">Hacer un pedido</Link>
      </header>

      <div className="inicio-rejilla">
        <article className="tarjeta inicio-carrito">
          <h2>Tu pedido en curso</h2>
          {unidades > 0 ? (
            <>
              <p>
                <strong>{unidades}</strong> {unidades === 1 ? 'unidad' : 'unidades'} de {carrito.length}{' '}
                {carrito.length === 1 ? 'producto' : 'productos'} · total estimado{' '}
                <strong>{dinero(estimarImportesPedido(carrito).total)}</strong> (IVA incluido)
              </p>
              <Link className="btn btn-primario" to="/carrito">Revisar y enviar</Link>
            </>
          ) : (
            <>
              <p className="texto-suave">Tu carrito está vacío.</p>
              <Link className="btn btn-secundario" to="/catalogo">Ir al catálogo</Link>
            </>
          )}
        </article>

        <TarjetaContador
          titulo="Pendientes de firma"
          valor={resumen?.pendientesFirma}
          cargando={cargando}
          explicacion={usuario?.rol === 'VETERINARIO'
            ? 'Pedidos de tu clínica que necesitan tu firma, los haya hecho quien los haya hecho.'
            : 'Pedidos que esperan la firma del veterinario responsable.'}
          enlace="/mis-pedidos?firma=PENDIENTE"
        />
        <TarjetaContador
          titulo="Pendientes de pago"
          valor={resumen?.pendientesPago}
          cargando={cargando}
          explicacion="Pedidos ya firmados que falta pagar para que se preparen."
          enlace="/mis-pedidos?estado=FIRMADO"
        />
        <TarjetaContador
          titulo="En preparación"
          valor={resumen?.enCamino}
          cargando={cargando}
          explicacion="Pedidos firmados y pagados que el almacén está preparando."
          enlace="/mis-pedidos"
        />
      </div>

      <ErrorBox mensaje={error} onReintentar={cargar} />
      {cargando && <Spinner texto="Cargando tus pedidos…" />}

      {!cargando && !error && (
        <div className="inicio-columnas">
          <article className="tarjeta">
            <h2>Últimos pedidos</h2>
            {resumen?.ultimosPedidos?.length > 0 ? (
              <ul className="lista-simple">
                {resumen.ultimosPedidos.slice(0, 3).map((p) => (
                  <li key={p.id}>
                    <Link to={`/pedidos/${p.id}`}>Pedido #{p.id}</Link>
                    <span className="texto-suave">{fechaCorta(p.fechaCreacion)}</span>
                    <BadgeEstadoPedido estado={p.estado} />
                  </li>
                ))}
              </ul>
            ) : (
              <p className="texto-suave">Tu clínica todavía no tiene pedidos.</p>
            )}
            <Link to="/mis-pedidos">Ver todos mis pedidos →</Link>
          </article>

          <article className="tarjeta">
            <h2>Productos que más pide tu clínica</h2>
            {habituales.length > 0 ? (
              <ul className="lista-simple">
                {habituales.map((p) => {
                  const disponible = p.activo !== false && Number(p.stockDisponible) > 0
                  return (
                    <li key={p.id}>
                      <span>
                        <strong>{p.nombre}</strong>
                        <small className="texto-suave"> · {dinero(p.precio)} sin IVA · {disponible ? `${p.stockDisponible} disponibles` : 'sin stock'}</small>
                      </span>
                      <button type="button" className="btn btn-mini btn-secundario" onClick={() => anadir(p)} disabled={!disponible}
                        aria-label={`Añadir 1 unidad de ${p.nombre} al carrito`}>
                        Añadir 1
                      </button>
                    </li>
                  )
                })}
              </ul>
            ) : (
              <p className="texto-suave">Cuando tu clínica haga pedidos, aquí aparecerán los productos que más pide para repetirlos rápido.</p>
            )}
          </article>
        </div>
      )}
    </section>
  )
}

function TarjetaContador({ titulo, valor, cargando, explicacion, enlace }) {
  return (
    <Link className="tarjeta inicio-contador" to={enlace}>
      <span className="inicio-contador-valor">{cargando ? '…' : (valor ?? 0)}</span>
      <span className="inicio-contador-titulo">{titulo}</span>
      <small className="texto-suave">{explicacion}</small>
    </Link>
  )
}

/** Suma las unidades pedidas de cada producto en los pedidos no cancelados de la clínica. */
function productosHabituales(pedidos, productos) {
  const unidadesPorProducto = new Map()
  for (const pedido of Array.isArray(pedidos) ? pedidos : []) {
    if (pedido.estado === 'CANCELADO') continue
    for (const linea of pedido.lineas || []) {
      unidadesPorProducto.set(linea.productoId, (unidadesPorProducto.get(linea.productoId) || 0) + linea.cantidad)
    }
  }
  const porId = new Map((Array.isArray(productos) ? productos : []).map((p) => [p.id, p]))
  return [...unidadesPorProducto.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([id]) => porId.get(id))
    .filter(Boolean)
    .slice(0, MAX_HABITUALES)
}
