import { useEffect, useState } from 'react'
import { api } from '../../api.js'
import { dinero } from '../../format.js'
import GraficoBarras from '../../components/GraficoBarras.jsx'
import { Spinner, ErrorBox, SinDatos } from '../../components/Spinner.jsx'

// Helpers tolerantes: los informes pueden variar en el nombre de los campos
function elegir(objeto, claves, porDefecto = null) {
  for (const c of claves) {
    if (objeto && objeto[c] != null) return objeto[c]
  }
  return porDefecto
}

function etiquetaFila(d, claves, sufijo) {
  const valor = elegir(d, claves)
  return valor != null ? `${valor}` : `${sufijo}`
}

function SeccionInforme({ titulo, descripcion, cargando, error, children }) {
  return (
    <div className="tarjeta informe">
      <h2>{titulo}</h2>
      {descripcion && <p className="texto-suave">{descripcion}</p>}
      {cargando ? <Spinner texto="Calculando informe…" /> : error ? <ErrorBox mensaje={error} /> : children}
    </div>
  )
}

export default function AdminInformes() {
  const [datos, setDatos] = useState({})
  const [errores, setErrores] = useState({})
  const [cargando, setCargando] = useState(true)

  async function cargar() {
    setCargando(true)
    const informes = {
      productosTop: '/api/informes/productos-top?limite=10',
      pedidosPorMes: '/api/informes/pedidos-por-mes',
      stockBajo: '/api/informes/stock-bajo',
      pendientes: '/api/informes/pendientes',
      tiempos: '/api/informes/tiempos',
      destinos: '/api/informes/destinos',
    }
    const nuevos = {}
    const nuevosErrores = {}
    await Promise.all(Object.entries(informes).map(async ([clave, url]) => {
      try { nuevos[clave] = await api.get(url) } catch (err) { nuevosErrores[clave] = err.message }
    }))
    setDatos(nuevos)
    setErrores(nuevosErrores)
    setCargando(false)
  }

  useEffect(() => { cargar() }, [])

  const productosTop = Array.isArray(datos.productosTop) ? datos.productosTop : []
  const porMes = Array.isArray(datos.pedidosPorMes) ? datos.pedidosPorMes : []
  const stockBajo = Array.isArray(datos.stockBajo) ? datos.stockBajo : []
  const destinos = Array.isArray(datos.destinos) ? datos.destinos : []
  const pendientes = datos.pendientes
  const tiempos = datos.tiempos

  return (
    <section>
      <h1>Informes</h1>
      <p className="texto-suave">Los seis informes del sistema, calculados con los datos reales del prototipo.</p>
      <div className="acciones-fila">
        <button type="button" className="btn btn-secundario" onClick={cargar} disabled={cargando}>🔄 Recalcular informes</button>
      </div>

      {/* 1. Productos más vendidos */}
      <SeccionInforme titulo="🏆 Productos más vendidos" descripcion="Unidades e importe facturado por producto."
        cargando={cargando} error={errores.productosTop}>
        {productosTop.length === 0 ? <SinDatos mensaje="Todavía no hay ventas registradas." /> : (
          <>
            <GraficoBarras
              datos={productosTop.map((d) => ({ etiqueta: etiquetaFila(d, ['productoNombre', 'nombre', 'producto'], 'Producto'), valor: Number(elegir(d, ['importe', 'importeTotal', 'total'], 0)) }))}
              formato={dinero}
              etiquetaSerie="Importe facturado"
              vacio="Sin importes que mostrar."
            />
            <div className="tabla-scroll">
              <table className="tabla">
                <thead><tr><th>Producto</th><th>Unidades</th><th>Importe</th></tr></thead>
                <tbody>
                  {productosTop.map((d, i) => (
                    <tr key={i}>
                      <td>{etiquetaFila(d, ['productoNombre', 'nombre', 'producto', 'codigo'], 'Producto')}</td>
                      <td>{elegir(d, ['unidades', 'cantidad', 'unidadesVendidas'], '—')}</td>
                      <td>{dinero(Number(elegir(d, ['importe', 'importeTotal', 'total'], 0)))}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </SeccionInforme>

      {/* 2. Pedidos por mes */}
      <SeccionInforme titulo="📅 Pedidos por mes" descripcion="Número de pedidos e importe de cada mes."
        cargando={cargando} error={errores.pedidosPorMes}>
        {porMes.length === 0 ? <SinDatos mensaje="Todavía no hay pedidos por mes." /> : (
          <>
            <GraficoBarras
              datos={porMes.map((d) => ({ etiqueta: etiquetaFila(d, ['mes', 'periodo'], 'Mes'), valor: Number(elegir(d, ['pedidos', 'numPedidos', 'cantidad'], 0)) }))}
              formato={(v) => String(v)}
              etiquetaSerie="Pedidos"
              vacio="Sin pedidos que mostrar."
            />
            <div className="tabla-scroll">
              <table className="tabla">
                <thead><tr><th>Mes</th><th>Pedidos</th><th>Importe</th></tr></thead>
                <tbody>
                  {porMes.map((d, i) => (
                    <tr key={i}>
                      <td>{etiquetaFila(d, ['mes', 'periodo'], 'Mes')}</td>
                      <td>{elegir(d, ['pedidos', 'numPedidos', 'cantidad'], '—')}</td>
                      <td>{dinero(Number(elegir(d, ['importe', 'importeTotal', 'total'], 0)))}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </SeccionInforme>

      {/* 3. Stock bajo */}
      <SeccionInforme titulo="⚠️ Productos con stock bajo" descripcion="Productos en o por debajo de su stock mínimo."
        cargando={cargando} error={errores.stockBajo}>
        {stockBajo.length === 0 ? <SinDatos mensaje="Ningún producto está por debajo de su stock mínimo. ¡Bien!" /> : (
          <div className="tabla-scroll">
            <table className="tabla">
              <thead><tr><th>Código</th><th>Producto</th><th>Stock disponible</th><th>Stock mínimo</th></tr></thead>
              <tbody>
                {stockBajo.map((p, i) => (
                  <tr key={p.id ?? i}>
                    <td>{elegir(p, ['codigo'], '—')}</td>
                    <td>{elegir(p, ['nombre', 'productoNombre'], '—')}</td>
                    <td><span className="etiqueta est-pago-fallado"><span aria-hidden="true">⚠️</span> {elegir(p, ['stockDisponible'], '—')}</span></td>
                    <td>{elegir(p, ['stockMinimo'], '—')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </SeccionInforme>

      {/* 4. Pendientes */}
      <SeccionInforme titulo="⏳ Pendientes de firma y de pago" descripcion="Pedidos que requieren una acción de la clínica."
        cargando={cargando} error={errores.pendientes}>
        {pendientes == null ? <SinDatos mensaje="No hay datos del informe de pendientes." /> : (
          Array.isArray(pendientes) ? (
            pendientes.length === 0 ? <SinDatos mensaje="No hay pedidos pendientes. Todo al día." /> : (
              <div className="tabla-scroll">
                <table className="tabla">
                  <thead><tr><th>Nº pedido</th><th>Clínica</th><th>Estado</th></tr></thead>
                  <tbody>
                    {pendientes.map((p, i) => (
                      <tr key={p.id ?? i}>
                        <td>#{elegir(p, ['id'], '—')}</td>
                        <td>{elegir(p, ['clinicaNombre'], '—')}</td>
                        <td>{elegir(p, ['estado'], '—')}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          ) : (
            <div className="rejilla-tarjetas">
              {Object.entries(pendientes).map(([clave, valor]) => (
                <div key={clave} className="tarjeta tarjeta-metrica">
                  <span className="metrica-valor">{typeof valor === 'number' ? valor : (valor?.length ?? '—')}</span>
                  <span className="metrica-etiqueta">{clave}</span>
                </div>
              ))}
            </div>
          )
        )}
      </SeccionInforme>

      {/* 5. Tiempos medios */}
      <SeccionInforme titulo="⏱️ Tiempos medios del proceso" descripcion="Media de horas desde la creación hasta la firma, el pago y la expedición."
        cargando={cargando} error={errores.tiempos}>
        {tiempos == null ? <SinDatos mensaje="No hay datos del informe de tiempos." /> : (
          Object.keys(tiempos).length === 0 ? <SinDatos mensaje="Aún no hay pedidos completados para calcular tiempos." /> : (
            <div className="tabla-scroll">
              <table className="tabla">
                <thead><tr><th>Hito</th><th>Tiempo medio</th></tr></thead>
                <tbody>
                  {Object.entries(tiempos).map(([clave, valor]) => (
                    <tr key={clave}>
                      <td>{{
                        mediaCreacionFirmaHoras: 'Creación a firma',
                        mediaFirmaPagoHoras: 'Intervalo entre firma y pago (cualquier orden)',
                        mediaPagoExpedicionHoras: 'Pago a expedición',
                        mediaTotalHoras: 'Proceso completo',
                      }[clave] || clave}</td>
                      <td>{typeof valor === 'number' ? `${valor} h` : String(valor)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        )}
      </SeccionInforme>

      {/* 6. Por destinos */}
      <SeccionInforme titulo="🚚 Pedidos y bultos por destino" descripcion="Volumen de expedición por destino."
        cargando={cargando} error={errores.destinos}>
        {destinos.length === 0 ? <SinDatos mensaje="Todavía no hay expediciones por destino." /> : (
          <>
            <GraficoBarras
              datos={destinos.map((d) => ({
                etiqueta: etiquetaFila(d, ['destinoNombre', 'destino', 'nombre'], 'Destino'),
                valor: Number(elegir(d, ['pedidos', 'numPedidos', 'totalPedidos'], 0)),
                valor2: Number(elegir(d, ['bultos', 'numBultos', 'totalBultos'], 0)),
              }))}
              formato={(v) => String(v)}
              etiquetaSerie="Pedidos"
              etiquetaSerie2="Bultos"
              vacio="Sin datos por destino."
            />
            <div className="tabla-scroll">
              <table className="tabla">
                <thead><tr><th>Destino</th><th>Pedidos</th><th>Bultos</th></tr></thead>
                <tbody>
                  {destinos.map((d, i) => (
                    <tr key={i}>
                      <td>{etiquetaFila(d, ['destinoNombre', 'destino', 'nombre'], 'Destino')}</td>
                      <td>{elegir(d, ['pedidos', 'numPedidos', 'totalPedidos'], '—')}</td>
                      <td>{elegir(d, ['bultos', 'numBultos', 'totalBultos'], '—')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </SeccionInforme>
    </section>
  )
}
