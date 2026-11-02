import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api.js'
import { dinero } from '../format.js'
import { ErrorBox } from '../components/Spinner.jsx'
import ImportesPedido from '../components/pedido/ImportesPedido.jsx'

/**
 * Pasarela de pago SIMULADA. Llama a POST /api/pagos/simulado/{referencia}
 * con resultado CONFIRMADO o FALLADO para poder probar ambos casos. El
 * backend envía entonces al webhook una notificación firmada (HMAC), igual
 * que haría un proveedor real. Se muestra SIEMPRE el estado que devuelve el
 * servidor, nunca el que se ha pedido.
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
  const [resultado, setResultado] = useState(null) // estado devuelto por el servidor: CONFIRMADO | FALLADO

  // Al cambiar de referencia (reintento) se vuelve a mostrar el formulario
  useEffect(() => { setResultado(null); setError(null) }, [referencia])

  function volverAlPedido() {
    navigate(pedidoId ? `/pedidos/${pedidoId}` : volver)
  }

  async function pagar(resultadoDeseado) {
    setProcesando(true)
    setError(null)
    try {
      const pago = await api.post(`/api/pagos/simulado/${encodeURIComponent(referencia)}`, { resultado: resultadoDeseado })
      setResultado(pago?.estado || null)
    } catch (err) {
      setError(err.message)
    } finally {
      setProcesando(false)
    }
  }

  /** Un intento FALLADO no se reutiliza: se pide al backend un pago nuevo con otra referencia. */
  async function reintentar() {
    setProcesando(true)
    setError(null)
    try {
      const nuevo = await api.post(`/api/pagos/${pedidoId}/iniciar`)
      navigate(`/pago-simulado/${encodeURIComponent(nuevo.referencia)}`, {
        replace: true,
        state: { ...datos, importe: nuevo.importe, moneda: nuevo.moneda },
      })
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

        <ErrorBox mensaje={error} />

        {resultado === 'CONFIRMADO' && (
          <div className="caja-ok">
            <span aria-hidden="true">✔️</span>
            <div className="caja-error-texto">
              Pago CONFIRMADO por la notificación del proveedor (simulado). El pedido ya está firmado y pagado:
              pasa a «Listo para preparar».
            </div>
          </div>
        )}
        {resultado === 'FALLADO' && (
          <div className="caja-error">
            <span aria-hidden="true">⚠️</span>
            <div className="caja-error-texto">
              El pago ha FALLADO (simulado). No se ha cobrado nada. Puedes reintentarlo: se creará un intento nuevo con otra referencia.
            </div>
          </div>
        )}
        {!resultado && (
          <>
            <div className="tarjeta-credito" aria-hidden="true">
              <div className="tarjeta-chip">▮▮▮</div>
              <div className="tarjeta-numero">•••• •••• •••• 0000</div>
              <div className="tarjeta-pie">
                <span>TARJETA DE DEMOSTRACIÓN</span>
                <span>00/00</span>
              </div>
            </div>
            <p className="texto-suave">
              Esta pasarela no pide ni guarda datos de tarjeta. Elige el resultado que quieres simular:
              el backend lo recibirá como una notificación firmada del proveedor.
            </p>
          </>
        )}

        <div className="acciones-fila">
          {!resultado && (
            <>
              <button type="button" className="btn btn-primario" onClick={() => pagar('CONFIRMADO')} disabled={procesando}>
                {procesando ? 'Procesando…' : 'Simular pago correcto'}
              </button>
              <button type="button" className="btn btn-peligro" onClick={() => pagar('FALLADO')} disabled={procesando}>
                Simular pago fallido
              </button>
              <Link className="btn btn-secundario" to={pedidoId ? `/pedidos/${pedidoId}` : volver}>
                Cancelar
              </Link>
            </>
          )}
          {resultado === 'FALLADO' && pedidoId && (
            <button type="button" className="btn btn-secundario" onClick={reintentar} disabled={procesando}>
              {procesando ? 'Creando nuevo intento…' : 'Reintentar el pago'}
            </button>
          )}
          {resultado && (
            <button type="button" className="btn btn-primario" onClick={volverAlPedido} disabled={procesando}>
              Volver al pedido
            </button>
          )}
        </div>
        {procesando && <p className="texto-suave">Contactando con la pasarela simulada…</p>}
      </div>
    </div>
  )
}
