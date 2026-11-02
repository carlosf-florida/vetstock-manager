import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import { ErrorBox, Spinner } from '../../components/Spinner.jsx'

/**
 * Panel de integración con el ERP Odoo (solo ADMIN).
 * Muestra el modo del adaptador, el estado de sincronización y el log.
 */
export default function AdminOdoo() {
  const [estado, setEstado] = useState(null)
  const [log, setLog] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [ocupado, setOcupado] = useState(null)
  const [mensaje, setMensaje] = useState(null)
  const [errorOp, setErrorOp] = useState(null)

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const [e, l] = await Promise.all([api.get('/api/odoo/estado'), api.get('/api/odoo/log')])
      setEstado(e)
      setLog(Array.isArray(l) ? l : [])
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }
  useEffect(() => { cargar() }, [])

  async function ejecutar(url, texto) {
    setOcupado(texto)
    setErrorOp(null)
    setMensaje(null)
    try {
      const respuesta = await api.post(url, {})
      await cargar()
      if (respuesta?.exito === false) {
        setErrorOp(respuesta.detalle || 'La operación con Odoo no se ha completado.')
        return
      }
      setMensaje(respuesta?.detalle || 'Operación realizada.')
    } catch (err) {
      setErrorOp(err.message)
    } finally {
      setOcupado(null)
    }
  }

  if (cargando) return <Spinner texto="Cargando la integración con Odoo..." />
  return (
    <section>
      <h1>Integración con Odoo (ERP)</h1>
      <p className="texto-suave">
        VetStock Manager se comunica con Odoo mediante un <strong>patrón adaptador</strong>:
        el adaptador SIMULADO funciona sin servidor (entorno de pruebas) y el REAL
        conecta con un servidor Odoo por JSON-RPC.
      </p>
      <ErrorBox mensaje={error} onReintentar={cargar} />

      {estado && (
        <div className="tarjeta">
          <h2>Estado</h2>
          <p>
            Modo actual: <strong>{estado.modo}</strong>{' '}
            {estado.modo === 'REAL'
              ? '(conectado a un servidor Odoo)'
              : '(sin servidor Odoo, adaptador simulado)'}
          </p>
          <ul>
            <li>Productos sincronizados: <strong>{estado.productoSincronizados}</strong> de {estado.productosTotales}</li>
            <li>Clientes (clínicas) sincronizados: <strong>{estado.clientesSincronizados}</strong></li>
            <li>Pedidos exportados: <strong>{estado.pedidosExportados}</strong></li>
          </ul>
          <div className="acciones-fila">
            <button className="boton" disabled={ocupado} onClick={() => ejecutar('/api/odoo/probar-conexion', 'Comprobando...')}>
              Probar conexión
            </button>
            <button className="boton" disabled={ocupado} onClick={() => ejecutar('/api/odoo/sincronizar/productos', 'Sincronizando...')}>
              Sincronizar productos
            </button>
            <button className="boton" disabled={ocupado} onClick={() => ejecutar('/api/odoo/sincronizar/clientes', 'Sincronizando...')}>
              Sincronizar clientes
            </button>
          </div>
          {ocupado && <Spinner texto={ocupado} />}
          {mensaje && <div className="caja-ok">{mensaje}</div>}
          {errorOp && <ErrorBox mensaje={errorOp} />}
          <p className="texto-suave">
            Tras confirmar un pago, el pedido se exporta automáticamente. Desde la ficha del pedido se puede reintentar una exportación pendiente o actualizar el mismo pedido ya vinculado.
          </p>
        </div>
      )}

      {log && (
        <div className="tarjeta">
          <h2>Últimas operaciones</h2>
          {log.length === 0 ? (
            <p className="texto-suave">Todavía no se ha sincronizado nada.</p>
          ) : (
            <table className="tabla">
              <thead>
                <tr><th>Fecha</th><th>Tipo</th><th>Referencia</th><th>Resultado</th><th>Detalle</th></tr>
              </thead>
              <tbody>
                {log.map((e) => (
                  <tr key={e.id}>
                    <td>{e.fecha}</td>
                    <td>{e.tipo}</td>
                    <td>{e.referencia || '—'}</td>
                    <td>{e.exito ? 'OK' : 'FALLO'}</td>
                    <td>{e.detalle}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </section>
  )
}
