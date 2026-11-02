import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../api.js'
import { dinero, fechaCorta, NOMBRE_ESTADO_PEDIDO } from '../../format.js'
import { Spinner, ErrorBox, SinDatos } from '../../components/Spinner.jsx'
import { BadgeEstadoPedido } from '../../components/Badges.jsx'

export default function AdminDashboard() {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      setDatos(await api.get('/api/dashboard'))
    } catch (err) {
      setError(err.message)
      setDatos(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargar() }, [])

  if (cargando) return <Spinner texto="Cargando el resumen…" />
  if (error) return <ErrorBox mensaje={error} onReintentar={cargar} />
  if (!datos) return <SinDatos mensaje="No se ha podido cargar el resumen." />

  const tarjetas = [
    // Cada tarjeta abre la lista con el mismo criterio con el que se cuenta en el backend
    { etiqueta: 'Pendientes de firma', valor: datos.pendientesFirma, icono: '✍️', enlace: '/admin/pedidos?situacion=FIRMA' },
    { etiqueta: 'Pendientes de pago', valor: datos.pendientesPago, icono: '💳', enlace: '/admin/pedidos?situacion=PAGO' },
    { etiqueta: 'Listos para preparar', valor: datos.listosPreparar, icono: '✅', enlace: '/admin/pedidos?situacion=LISTO' },
    { etiqueta: 'En preparación', valor: datos.enPreparacion, icono: '🛠️', enlace: '/admin/pedidos?estado=EN_PREPARACION' },
    { etiqueta: 'Preparados', valor: datos.preparados, icono: '📦', enlace: '/admin/pedidos?estado=PREPARADO' },
    { etiqueta: 'Expedidos', valor: datos.expedidos, icono: '🚚', enlace: '/admin/pedidos?estado=EXPEDIDO' },
    { etiqueta: 'Productos con stock bajo', valor: datos.productosStockBajo, icono: '⚠️', enlace: '/admin/productos?bajoStock=1' },
  ]

  const ultimos = datos.ultimosPedidos || []

  return (
    <section>
      <h1>Resumen del panel de administración</h1>
      <p className="texto-suave">Situación general de pedidos y stock de la distribuidora.</p>

      <div className="rejilla-tarjetas">
        {tarjetas.map((t) => (
          <Link key={t.etiqueta} to={t.enlace} className="tarjeta tarjeta-metrica">
            <span className="metrica-icono" aria-hidden="true">{t.icono}</span>
            <span className="metrica-valor">{t.valor ?? 0}</span>
            <span className="metrica-etiqueta">{t.etiqueta}</span>
          </Link>
        ))}
      </div>

      <h2>Últimos pedidos</h2>
      {ultimos.length === 0 ? (
        <SinDatos mensaje="Todavía no hay pedidos registrados." />
      ) : (
        <div className="tabla-scroll">
          <table className="tabla">
            <thead>
              <tr><th>Nº</th><th>Clínica</th><th>Fecha</th><th>Estado</th><th>Total</th><th></th></tr>
            </thead>
            <tbody>
              {ultimos.map((p) => (
                <tr key={p.id}>
                  <td>#{p.id}</td>
                  <td>{p.clinicaNombre}</td>
                  <td>{fechaCorta(p.fechaCreacion)}</td>
                  <td><BadgeEstadoPedido estado={p.estado} /></td>
                  <td>{dinero(p.total)}</td>
                  <td><Link className="btn btn-mini btn-secundario" to={`/admin/pedidos/${p.id}`}>Gestionar</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p><Link to="/admin/pedidos" className="btn btn-secundario">Ver todos los pedidos</Link></p>
    </section>
  )
}
