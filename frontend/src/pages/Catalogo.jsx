import { useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { dinero } from '../format.js'
import { anadirAlCarrito } from '../carrito.js'
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
  const [mensaje, setMensaje] = useState(null)

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      if (busqueda.trim()) params.set('busqueda', busqueda.trim())
      if (categoria) params.set('categoria', categoria)
      if (soloStock) params.set('soloBajoStock', 'true')
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
  }, [busqueda, categoria, soloStock])

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
    return salida
  }, [productos, precioMax])

  function agregar(p) {
    anadirAlCarrito(p, 1)
    setMensaje(`«${p.nombre}» añadido al carrito.`)
    setTimeout(() => setMensaje(null), 2500)
  }

  return (
    <section>
      <h1>Catálogo de productos</h1>
      <p className="texto-suave">Busca productos veterinarios, filtra por categoría, precio o stock y añádelos al carrito.</p>
      <p className="texto-suave">Los precios se muestran sin IVA. Para los pedidos nuevos se aplicará el IVA del 4 % configurado para esta prueba.</p>

      {mensaje && <div className="caja-ok"><span aria-hidden="true">✔️</span> {mensaje}</div>}

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
                  <button
                    type="button"
                    className="btn btn-primario"
                    onClick={() => agregar(p)}
                    disabled={sinStock || inactivo}
                  >
                    {sinStock ? 'Sin stock' : 'Añadir al carrito'}
                  </button>
                </div>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
