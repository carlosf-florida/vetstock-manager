import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import { fecha } from '../../format.js'
import Tabla from '../../components/Tabla.jsx'
import { ErrorBox, Spinner } from '../../components/Spinner.jsx'

export default function AdminStock() {
  const [productos, setProductos] = useState(null)
  const [productoId, setProductoId] = useState('')
  const [tipo, setTipo] = useState('entrada')
  const [cantidad, setCantidad] = useState('')
  const [motivo, setMotivo] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState(null)
  const [ok, setOk] = useState(null)
  const [guardando, setGuardando] = useState(false)

  const [movimientos, setMovimientos] = useState(null)
  const [cargandoMovs, setCargandoMovs] = useState(true)
  const [errorMovs, setErrorMovs] = useState(null)
  const [filtroProducto, setFiltroProducto] = useState('')

  useEffect(() => {
    api.get('/api/productos')
      .then((datos) => setProductos(Array.isArray(datos) ? datos : []))
      .catch((err) => setError(err.message))
  }, [])

  async function cargarMovimientos() {
    setCargandoMovs(true)
    setErrorMovs(null)
    try {
      const url = '/api/stock/movimientos' + (filtroProducto ? `?productoId=${filtroProducto}` : '')
      const datos = await api.get(url)
      setMovimientos(Array.isArray(datos) ? datos : [])
    } catch (err) {
      setErrorMovs(err.message)
      setMovimientos(null)
    } finally {
      setCargandoMovs(false)
    }
  }

  useEffect(() => { cargarMovimientos() }, [filtroProducto])

  function avisar(texto) {
    setOk(texto)
    setTimeout(() => setOk(null), 3500)
  }

  async function registrar(e) {
    e.preventDefault()
    const errs = {}
    if (!productoId) errs.productoId = 'Elige un producto.'
    const cant = Number(cantidad)
    if (!Number.isInteger(cant) || cant <= 0) errs.cantidad = 'La cantidad debe ser un entero mayor que 0.'
    if (!motivo.trim()) errs.motivo = 'Indica un motivo para el movimiento.'
    setErrores(errs)
    if (Object.keys(errs).length) return

    setGuardando(true)
    setError(null)
    try {
      await api.post(`/api/stock/${tipo}`, {
        productoId: Number(productoId),
        cantidad: cant,
        motivo: motivo.trim(),
      })
      setCantidad('')
      setMotivo('')
      avisar(tipo === 'entrada' ? 'Entrada de stock registrada.' : 'Salida de stock registrada.')
      cargarMovimientos()
    } catch (err) {
      setError(err.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section>
      <h1>Gestión de stock</h1>
      <p className="texto-suave">Registra entradas y salidas de mercancía y consulta el historial de movimientos.</p>

      {ok && <div className="caja-ok"><span aria-hidden="true">✔️</span> {ok}</div>}
      <ErrorBox mensaje={error} />

      <div className="tarjeta">
        <h2>Registrar movimiento</h2>
        {!productos ? (
          <Spinner texto="Cargando productos…" />
        ) : (
          <form onSubmit={registrar} noValidate>
            <div className="campo-trio">
              <label className="campo">
                <span>Producto *</span>
                <select className="input" value={productoId} onChange={(e) => setProductoId(e.target.value)}>
                  <option value="">— Elige un producto —</option>
                  {productos.map((p) => (
                    <option key={p.id} value={p.id}>{p.codigo} · {p.nombre} (disp.: {p.stockDisponible})</option>
                  ))}
                </select>
                {errores.productoId && <small className="error-campo">{errores.productoId}</small>}
              </label>
              <label className="campo">
                <span>Tipo de movimiento</span>
                <select className="input" value={tipo} onChange={(e) => setTipo(e.target.value)}>
                  <option value="entrada">📥 Entrada</option>
                  <option value="salida">📤 Salida</option>
                </select>
              </label>
              <label className="campo">
                <span>Cantidad *</span>
                <input className="input" type="number" min="1" step="1" value={cantidad} onChange={(e) => setCantidad(e.target.value)} />
                {errores.cantidad && <small className="error-campo">{errores.cantidad}</small>}
              </label>
            </div>
            <label className="campo">
              <span>Motivo *</span>
              <input className="input" value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Recepción de pedido del proveedor, rotura, recuento…" />
              {errores.motivo && <small className="error-campo">{errores.motivo}</small>}
            </label>
            <div className="acciones-fila">
              <button type="submit" className="btn btn-primario" disabled={guardando}>
                {guardando ? 'Registrando…' : tipo === 'entrada' ? '📥 Registrar entrada' : '📤 Registrar salida'}
              </button>
            </div>
          </form>
        )}
      </div>

      <h2>Historial de movimientos</h2>
      <Tabla
        columns={[
          { key: 'fecha', label: 'Fecha', render: (m) => fecha(m.fecha) },
          { key: 'producto', label: 'Producto', render: (m) => m.nombreProducto || m.productoNombre || m.productoId || '—' },
          { key: 'tipo', label: 'Tipo', render: (m) => m.tipo === 'ENTRADA' || m.tipo === 'entrada'
            ? <span className="etiqueta est-expedido"><span aria-hidden="true">📥</span> Entrada</span>
            : <span className="etiqueta est-pago-fallado"><span aria-hidden="true">📤</span> Salida</span> },
          { key: 'cantidad', label: 'Cantidad' },
          { key: 'motivo', label: 'Motivo' },
          { key: 'usuario', label: 'Usuario', render: (m) => m.username || m.usuario || '—', soloEscritorio: true },
        ]}
        filas={movimientos || []}
        claveFila={(m, i) => m.id ?? i}
        busquedaCampos={['motivo', 'nombreProducto', 'productoNombre', 'username']}
        placeholderBusqueda="Buscar por motivo o producto…"
        filtros={[
          {
            etiqueta: 'Producto',
            valor: filtroProducto,
            setValor: setFiltroProducto,
            opciones: [{ value: '', label: 'Todos los productos' }, ...(productos || []).map((p) => ({ value: String(p.id), label: p.nombre }))],
            cumple: (m, v) => String(m.productoId) === v,
          },
        ]}
        textoVacio="No hay movimientos registrados todavía."
        cargando={cargandoMovs}
      />
      <ErrorBox mensaje={errorMovs} onReintentar={cargarMovimientos} />
    </section>
  )
}
