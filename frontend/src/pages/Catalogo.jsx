import { useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { dinero } from '../format.js'
import { anadirAlCarrito } from '../carrito.js'
import { useNotificaciones } from '../components/Notificaciones.jsx'
import { Spinner, ErrorBox, SinDatos } from '../components/Spinner.jsx'
import { BadgeStockBajo } from '../components/Badges.jsx'

export default function Catalogo() {
  const [productos, setProductos] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)

  const [busqueda, setBusqueda] = useState('')
  const [categoria, setCategoria] = useState('')
  const [soloStock, setSoloStock] = useState(false)
  const [precioMax, setPrecioMax] = useState('')
  const [cantidades, setCantidades] = useState({}) // unidades elegidas en cada tarjeta
  const { notificar } = useNotificaciones()

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      if (busqueda.trim()) params.set('busqueda', busqueda.trim())
      if (categoria) params.set('categoria', categoria)
      const datos = await api.get('/api/productos' + (params.toString() ? `?${params}` : ''))
      setProductos(Array.isArray(datos) ? datos : [])
    } catch (err) {
      setError(err.message)
      setProductos(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    const t = setTimeout(cargar, 250) // pequeña espera para no disparar por cada tecla
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [busqueda, categoria])

  const categorias = useMemo(() => {
    if (!productos) return []
    return [...new Set(productos.map((p) => p.categoria).filter(Boolean))].sort()
  }, [productos])

  const visibles = useMemo(() => {
    let salida = productos || []
    const max = Number(precioMax)
    if (precioMax !== '' && Number.isFinite(max)) {
      salida = salida.filter((p) => Number(p.precio) <= max)
    }
    // Antes se enviaba soloBajoStock al servidor y salían justo los de stock BAJO
    if (soloStock) salida = salida.filter((p) => Number(p.stockDisponible) > 0)
    return salida
  }, [productos, precioMax, soloStock])

  function agregar(p) {
    const cantidad = Number(cantidades[p.id] ?? 1)
    const resultado = anadirAlCarrito(p, cantidad)
    if (!resultado.ok) {
      notificar({ tipo: 'error', texto: resultado.mensaje, enlace: { texto: 'Ver carrito', a: '/carrito' } })
      return
    }
    notificar({
      texto: `${cantidad === 1 ? 'Se ha añadido 1 unidad' : `Se han añadido ${cantidad} unidades`} de «${p.nombre}» al carrito.`,
      enlace: { texto: 'Ver carrito', a: '/carrito' },
    })
    setCantidades((actual) => ({ ...actual, [p.id]: 1 }))
  }

  return (
    <section>
      <h1>Catálogo de productos</h1>
      <p className="texto-suave">Busca productos veterinarios, filtra por categoría, precio o stock y añádelos al carrito.</p>
      <p className="texto-suave">Los precios se muestran sin IVA. Para los pedidos nuevos se aplicará el IVA del 4 % configurado para esta prueba.</p>


      <div className="filtros-panel">
        <label className="campo">
          <span>Buscar por texto</span>
          <input className="input" type="search" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Nombre o código…" />
        </label>
        <label className="campo">
          <span>Categoría</span>
          <select className="input" value={categoria} onChange={(e) => setCategoria(e.target.value)}>
            <option value="">Todas las categorías</option>
            {categorias.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
        <label className="campo">
          <span>Precio máximo (€)</span>
          <input className="input" type="number" min="0" step="0.01" value={precioMax} onChange={(e) => setPrecioMax(e.target.value)} placeholder="Sin límite" />
        </label>
        <label className="campo campo-checkbox">
          <input type="checkbox" checked={soloStock} onChange={(e) => setSoloStock(e.target.checked)} />
          <span>Solo con stock disponible</span>
        </label>
      </div>

      <ErrorBox mensaje={error} onReintentar={cargar} />
      {cargando && <Spinner texto="Cargando catálogo…" />}

      {!cargando && !error && visibles.length === 0 && (
        <SinDatos mensaje="No hay productos que coincidan con la búsqueda. Prueba a quitar algún filtro." />
      )}

      {visibles.length > 0 && (
        <div className="rejilla-productos">
          {visibles.map((p) => {
            const sinStock = Number(p.stockDisponible) <= 0
            const inactivo = p.activo === false
            const stockBajo = !sinStock && p.stockMinimo != null && Number(p.stockDisponible) <= Number(p.stockMinimo)
            return (
              <article key={p.id} className={'tarjeta producto' + (inactivo ? ' producto-inactivo' : '')}>
                <div className="producto-cabecera">
                  <span className="producto-codigo">{p.codigo}</span>
                  <BadgeStockBajo bajo={stockBajo} />
                  {inactivo && <span className="etiqueta est-cancelado">⛔ No disponible</span>}
                </div>
                <h2 className="producto-nombre">{p.nombre}</h2>
                {p.descripcion && <p className="producto-descripcion">{p.descripcion}</p>}
                <p className="producto-meta">
                  {p.categoria && <span className="chip">{p.categoria}</span>}
                  <span>Stock disponible: <strong>{p.stockDisponible ?? '—'}</strong></span>
                </p>
                <div className="producto-pie">
                  <span className="producto-precio">{dinero(p.precio)} <small>sin IVA</small></span>
                </div>
                {!sinStock && !inactivo ? (
                  <div className="producto-anadir">
                    <label className="producto-cantidad">
                      <span>Unidades</span>
                      <input
                        className="input"
                        type="number"
                        min="1"
                        max={p.stockDisponible}
                        step="1"
                        value={cantidades[p.id] ?? 1}
                        onChange={(e) => setCantidades((actual) => ({ ...actual, [p.id]: e.target.value }))}
                        aria-label={`Unidades de ${p.nombre}`}
                      />
                    </label>
                    <button type="button" className="btn btn-primario" onClick={() => agregar(p)}>
                      Añadir al carrito
                    </button>
                  </div>
                ) : (
                  <p className="texto-suave producto-no-disponible">{inactivo ? 'No disponible' : 'Sin stock disponible'}</p>
                )}
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
