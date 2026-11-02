import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import { dinero } from '../../format.js'
import { ErrorBox, SinDatos } from '../Spinner.jsx'

/**
 * Líneas del pedido. Si `editable` (pedido en BORRADOR y usuario con permiso),
 * permite cambiar cantidades y guardar con PUT /api/pedidos/{id}/lineas.
 */
export default function LineasPedido({ pedido, editable, onGuardado }) {
  const [lineas, setLineas] = useState(Array.isArray(pedido.lineas) ? pedido.lineas : [])
  const [error, setError] = useState(null)
  const [ok, setOk] = useState(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    setLineas(Array.isArray(pedido.lineas) ? pedido.lineas : [])
  }, [pedido.id, pedido.lineas])

  function cambiar(idLinea, valor) {
    const cantidad = Number(valor)
    setLineas((actuales) => actuales.map((l) =>
      l.id === idLinea || l.productoId === idLinea
        ? { ...l, cantidad: Number.isInteger(cantidad) && cantidad > 0 ? cantidad : l.cantidad }
        : l,
    ))
  }

  async function guardar() {
    const invalidas = lineas.filter((l) => !Number.isInteger(Number(l.cantidad)) || Number(l.cantidad) < 1)
    if (invalidas.length > 0) {
      setError('Todas las cantidades deben ser enteros de 1 o más.')
      return
    }
    setGuardando(true)
    setError(null)
    try {
      await api.put(`/api/pedidos/${pedido.id}/lineas`, {
        lineas: lineas.map((l) => ({ productoId: l.productoId, cantidad: Number(l.cantidad) })),
      })
      setOk('Líneas actualizadas.')
      onGuardado?.()
      setTimeout(() => setOk(null), 3000)
    } catch (err) {
      setError(err.message)
    } finally {
      setGuardando(false)
    }
  }

  if (!lineas.length) return <SinDatos mensaje="El pedido no tiene líneas todavía." />

  return (
    <div>
      <ErrorBox mensaje={error} />
      {ok && <div className="caja-ok"><span aria-hidden="true">✔️</span> {ok}</div>}
      <div className="tabla-scroll">
        <table className="tabla">
          <thead>
            <tr>
              <th>Producto</th>
              <th>Código</th>
              <th>Cantidad</th>
              <th>Precio unitario</th>
              <th>Subtotal</th>
            </tr>
          </thead>
          <tbody>
            {lineas.map((l) => {
              const precio = Number(l.precioUnitario ?? l.precio ?? 0)
              const cantidad = Number(l.cantidad ?? 0)
              return (
                <tr key={l.id ?? l.productoId}>
                  <td>{l.nombreProducto || l.nombre || `Producto #${l.productoId}`}</td>
                  <td>{l.codigoProducto || l.codigo || '—'}</td>
                  <td>
                    {editable ? (
                      <input
                        className="input input-cantidad"
                        type="number"
                        min="1"
                        step="1"
                        value={cantidad}
                        onChange={(e) => cambiar(l.id ?? l.productoId, e.target.value)}
                        aria-label={`Cantidad de ${l.nombreProducto || l.nombre || l.productoId}`}
                      />
                    ) : (
                      cantidad
                    )}
                  </td>
                  <td>{dinero(precio)}</td>
                  <td>{dinero(precio * cantidad)}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {editable && (
        <div className="acciones-fila">
          <button type="button" className="btn btn-primario" onClick={guardar} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar cantidades'}
          </button>
        </div>
      )}
    </div>
  )
}
