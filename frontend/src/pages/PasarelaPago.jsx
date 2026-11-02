import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api.js'
import { dinero } from '../format.js'
import { ErrorBox } from '../components/Spinner.jsx'
import ImportesPedido from '../components/pedido/ImportesPedido.jsx'

/**
 * Pasarela de pago SIMULADA. Llama a POST /api/pagos/simulado/{referencia}
 * con resultado CONFIRMADO o FALLADO para poder probar ambos casos.
 */
export default function PasarelaPago() {
  const { referencia } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const datos = location.state || {}
  const volver = datos.volver || '/mis-pedidos'
  const pedidoId = datos.pedidoId

  const [procesando, setProcesando] = useState(false)
  const [error, setError] = useState(null)
  const [resultado, setResultado] = useState(null) // 'CONFIRMADO' | 'FALLADO'

  function volverACargo() {
    navigate(pedidoId ? `/pedidos/${pedidoId}` : volver)
  }

  async function pagar(resultadoDeseado) {
    setProcesando(true)
    setError(null)
    try {
      await api.post(`/api/pagos/simulado/${encodeURIComponent(referencia)}`, { resultado: resultadoDeseado })
      setResultado(resultadoDeseado)
    } catch (err) {
      setError(err.message)
    } finally {
      setProcesando(false)
    }
  }

  return (
    <div className="pasarela-fondo">
      <div className="pasarela-tarjeta">
        <div className="pasarela-aviso" role="note">
          Pasarela de pago <strong>SIMULADA</strong> · entorno de pruebas, no se realiza ningún cobro real
        </div>

        <h1><span aria-hidden="true">💳</span> Pasarela de pago</h1>
        <p className="texto-suave">
          Referencia: <code>{referencia}</code>
          {datos.importe != null && <> · Importe: <strong>{dinero(datos.importe)}</strong></>}
          {datos.moneda && <> · Moneda: {datos.moneda}</>}
          {' '}· Proveedor: SIMULADO
        </p>

        <ImportesPedido importes={datos.importes || { total: datos.importe, moneda: datos.moneda }} />

        <ErrorBox mensaje={error} onReintentar={() => pagar('CONFIRMADO')} />

        {resultado ? (
          <div className={resultado === 'CONFIRMADO' ? 'caja-ok' : 'caja-error'}>
            <span aria-hidden="true">{resultado === 'CONFIRMADO' ? '✔️' : '⚠️'}</span>
            <div className="caja-error-texto">
              {resultado === 'CONFIRMADO'
                ? 'El pago se ha CONFIRMADO en la pasarela simulada. Si falta la firma, el pedido seguirá pendiente de firma; solo con pago y firma pasará a «Listo para preparar».'
                : 'El pago ha FALLADO (simulado). Puedes reintentarlo desde el detalle del pedido.'}
            </div>
          </div>
        ) : (
          <>
            <div className="tarjeta-credito" aria-hidden="true">
              <div className="tarjeta-chip">▮▮▮</div>
              <div className="tarjeta-numero">•••• •••• •••• 0000</div>
              <div className="tarjeta-pie">
                <span>TITULAR DE PRUEBAS</span>
                <span>00/00</span>
              </div>
            </div>

            <div className="tarjeta formulario-pasarela">
              <label className="campo"><span>Titular (ficticio)</span><input className="input" value="PRUEBAS VETSTOCK" readOnly /></label>
              <label className="campo"><span>Número de tarjeta (ficticio)</span><input className="input" value="0000 0000 0000 0000" readOnly /></label>
              <div className="campo-doble">
                <label className="campo"><span>Caducidad</span><input className="input" value="00/00" readOnly /></label>
                <label className="campo"><span>CVC</span><input className="input" value="000" readOnly /></label>
              </div>
              <p className="texto-suave">Estos campos son decorativos: no se envían a ningún sitio y no representan una tarjeta real.</p>
            </div>
          </>
        )}

        <div className="acciones-fila">
          {!resultado && (
            <>
              <button type="button" className="btn btn-primario" onClick={() => pagar('CONFIRMADO')} disabled={procesando}>
                {procesando ? 'Procesando…' : 'Pagar'}
              </button>
              <button type="button" className="btn btn-peligro" onClick={() => pagar('FALLADO')} disabled={procesando}>
                Fallar el pago
              </button>
            </>
          )}
          {resultado === 'CONFIRMADO' && (
            <button type="button" className="btn btn-primario" onClick={() => volverACargo()} disabled={procesando}>
              Volver al pedido
            </button>
          )}
          {resultado === 'FALLADO' && (
            <>
              <button type="button" className="btn btn-secundario" onClick={() => setResultado(null)} disabled={procesando}>
                Reintentar en la pasarela
              </button>
              <button type="button" className="btn btn-primario" onClick={volverACargo} disabled={procesando}>
                Volver al pedido
              </button>
            </>
          )}
          {!resultado && (
            <Link className="btn btn-secundario" to={pedidoId ? `/pedidos/${pedidoId}` : volver} onClick={() => {}}>
              Cancelar
            </Link>
          )}
        </div>
        {procesando && <p className="texto-suave">Contactando con la pasarela simulada…</p>}
      </div>
    </div>
  )
}
