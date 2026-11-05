import { useState } from 'react'
import { Link } from 'react-router-dom'
import { fechaCorta, kg, NOMBRE_ESTADO_PEDIDO } from '../../format.js'
import { SinDatos } from '../Spinner.jsx'

/**
 * Listado para el almacén: pedidos con salida asignada, separados por salida, para imprimirlo
 * y comprobar en papel que están todos. Solo muestra datos; no cambia ningún estado.
 */
export default function ListadoPorSalida({ pedidos, cargando }) {
  const [salida, setSalida] = useState('')

  const conSalida = pedidos.filter((p) => p.salida && !['CANCELADO', 'BORRADOR'].includes(p.estado))
  const salidas = [...new Set(conSalida.map((p) => p.salida))].sort()
  const visibles = salidas.filter((s) => !salida || s === salida)

  if (cargando) return <p className="cargando-tabla">Cargando datos…</p>
  if (conSalida.length === 0) {
    return <SinDatos mensaje="Todavía no hay pedidos con salida. La salida se asigna al marcar el pedido como preparado." />
  }

  return (
    <div className="listado-salidas">
      <div className="listado-salidas-controles no-imprimir">
        <label className="tabla-filtro">
          <span>Salida</span>
          <select className="input" value={salida} onChange={(e) => setSalida(e.target.value)}>
            <option value="">Todas las salidas</option>
            {salidas.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
        <button type="button" className="btn btn-primario" onClick={() => window.print()}>Imprimir listado</button>
      </div>

      <p className="solo-imprimir">Listado de pedidos por salida · {fechaCorta(new Date().toISOString())}</p>

      {visibles.map((s) => {
        const deLaSalida = conSalida.filter((p) => p.salida === s).sort((a, b) => a.id - b.id)
        const bultos = deLaSalida.reduce((suma, p) => suma + (p.bultos ?? 0), 0)
        return (
          <section key={s} className="tarjeta listado-salida">
            <h2>Salida {s}</h2>
            <p className="texto-suave">{deLaSalida.length} {deLaSalida.length === 1 ? 'pedido' : 'pedidos'} · {bultos} {bultos === 1 ? 'bulto' : 'bultos'}</p>
            <div className="tabla-scroll">
              <table className="tabla">
                <thead>
                  <tr><th>Pedido</th><th>Clínica</th><th>Dirección de entrega</th><th>Estado</th><th>Bultos</th><th>Peso</th><th>Comprobado</th></tr>
                </thead>
                <tbody>
                  {deLaSalida.map((p) => (
                    <tr key={p.id}>
                      <td><Link to={`/admin/pedidos/${p.id}`}>#{p.id}</Link></td>
                      <td>{p.clinicaNombre}</td>
                      <td>{p.direccionEntrega || '—'}</td>
                      <td>{NOMBRE_ESTADO_PEDIDO(p.estado)}</td>
                      <td>{p.bultos ?? 0}</td>
                      <td>{p.bultosCalculados == null ? 'Faltan pesos' : `${kg(p.pesoTotalKg)} kg`}</td>
                      <td><span className="casilla-papel" role="img" aria-label="Casilla para marcar en papel" /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )
      })}
    </div>
  )
}
