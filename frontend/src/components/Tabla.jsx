import { useEffect, useMemo, useState } from 'react'
import { SinDatos } from './Spinner.jsx'

/**
 * Tabla genérica con búsqueda de texto, filtros y paginación en cliente.
 * columns: [{ key, label, render?(fila), soloEscritorio? }]
 * busquedaCampos: claves de la fila (o función) sobre las que busca el texto.
 * filtros: [{ etiqueta, valor, setValor, opciones:[{value,label}], cumple(fila, valor) }]
 */
export default function Tabla({
  columns,
  filas,
  claveFila = (f, i) => i,
  busquedaCampos = [],
  busquedaValor = '',
  onBusqueda = null,
  placeholderBusqueda = 'Buscar…',
  filtros = [],
  tamanoPagina = 10,
  textoVacio = 'No hay datos para mostrar.',
  cargando = false,
}) {
  const [pagina, setPagina] = useState(1)

  useEffect(() => { setPagina(1) }, [filas, busquedaValor, JSON.stringify(filtros.map(f => f.valor))])

  const filtradas = useMemo(() => {
    let salida = filas
    const texto = busquedaValor.trim().toLowerCase()
    if (texto) {
      salida = salida.filter((f) =>
        busquedaCampos.some((campo) => {
          const valor = typeof campo === 'function' ? campo(f) : f[campo]
          return String(valor ?? '').toLowerCase().includes(texto)
        }),
      )
    }
    for (const f of filtros) {
      if (f.valor !== '' && f.valor != null && f.cumple) {
        salida = salida.filter((fila) => f.cumple(fila, f.valor))
      }
    }
    return salida
  }, [filas, busquedaValor, filtros, busquedaCampos])

  const paginas = Math.max(1, Math.ceil(filtradas.length / tamanoPagina))
  const paginaSegura = Math.min(pagina, paginas)
  const visibles = filtradas.slice((paginaSegura - 1) * tamanoPagina, paginaSegura * tamanoPagina)

  return (
    <div className="tabla-zona">
      {(onBusqueda || filtros.length > 0) && (
        <div className="tabla-controles">
          {onBusqueda && (
            <input
              type="search"
              className="input"
              placeholder={placeholderBusqueda}
              value={busquedaValor}
              onChange={(e) => onBusqueda(e.target.value)}
              aria-label={placeholderBusqueda}
            />
          )}
          {filtros.map((f) => (
            <label key={f.etiqueta} className="tabla-filtro">
              <span>{f.etiqueta}</span>
              <select
                className="input"
                value={f.valor}
                onChange={(e) => f.setValor(e.target.value)}
              >
                {f.opciones.map((op) => (
                  <option key={String(op.value)} value={op.value}>{op.label}</option>
                ))}
              </select>
            </label>
          ))}
        </div>
      )}

      {cargando ? (
        <p className="cargando-tabla">Cargando datos…</p>
      ) : visibles.length === 0 ? (
        <SinDatos mensaje={textoVacio} />
      ) : (
        <div className="tabla-scroll">
          <table className="tabla">
            <thead>
              <tr>
                {columns.map((c) => (
                  <th key={c.key} className={c.soloEscritorio ? 'solo-escritorio' : ''}>{c.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visibles.map((fila, i) => (
                <tr key={claveFila(fila, i)}>
                  {columns.map((c) => (
                    <td key={c.key} className={c.soloEscritorio ? 'solo-escritorio' : ''}>
                      {c.render ? c.render(fila) : String(fila[c.key] ?? '—')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {paginas > 1 && !cargando && visibles.length > 0 && (
        <div className="paginacion">
          <button type="button" className="btn btn-secundario" disabled={paginaSegura <= 1} onClick={() => setPagina(paginaSegura - 1)}>
            ← Anterior
          </button>
          <span>Página {paginaSegura} de {paginas} · {filtradas.length} registros</span>
          <button type="button" className="btn btn-secundario" disabled={paginaSegura >= paginas} onClick={() => setPagina(paginaSegura + 1)}>
            Siguiente →
          </button>
        </div>
      )}
    </div>
  )
}
