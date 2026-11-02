import { dinero } from '../format.js'
import { SinDatos } from './Spinner.jsx'

/**
 * Gráfico de barras horizontal hecho solo con CSS a partir de datos reales.
 * datos: [{ etiqueta, valor, valor2?, etiqueta2? }] — valor2 opcional (segunda barra).
 */
export default function GraficoBarras({ datos, formato = (v) => String(v), etiquetaSerie = 'Valor', etiquetaSerie2 = null, vacio = 'Sin datos todavía.' }) {
  if (!datos || datos.length === 0) return <SinDatos mensaje={vacio} />
  const max = Math.max(...datos.flatMap((d) => [d.valor ?? 0, d.valor2 ?? 0]), 0)
  if (max <= 0) return <SinDatos mensaje={vacio} />

  return (
    <div className="grafico">
      {(etiquetaSerie2 || etiquetaSerie) && (
        <div className="grafico-leyenda">
          <span><i className="leyenda leyenda-1" aria-hidden="true"></i> {etiquetaSerie}</span>
          {etiquetaSerie2 && <span><i className="leyenda leyenda-2" aria-hidden="true"></i> {etiquetaSerie2}</span>}
        </div>
      )}
      <ul className="grafico-filas">
        {datos.map((d, i) => (
          <li key={i} className="grafico-fila">
            <span className="grafico-etiqueta" title={d.etiqueta}>{d.etiqueta}</span>
            <span className="grafico-barras">
              <span className="grafico-barra barra-1" style={{ width: `${((d.valor ?? 0) / max) * 100}%` }}>
                <span className="grafico-valor">{formato(d.valor ?? 0)}</span>
              </span>
              {d.valor2 != null && (
                <span className="grafico-barra barra-2" style={{ width: `${((d.valor2 ?? 0) / max) * 100}%` }}>
                  <span className="grafico-valor">{formato(d.valor2 ?? 0)}</span>
                </span>
              )}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
