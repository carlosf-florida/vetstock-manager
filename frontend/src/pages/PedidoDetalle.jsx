import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Spinner, ErrorBox } from '../components/Spinner.jsx'
import { BadgeEstadoPedido } from '../components/Badges.jsx'
import LineasPedido from '../components/pedido/LineasPedido.jsx'
import CronologiaFechas from '../components/pedido/CronologiaFechas.jsx'
import DocumentosSeccion from '../components/pedido/DocumentosSeccion.jsx'
import PagoSeccion from '../components/pedido/PagoSeccion.jsx'
import FirmaSeccion from '../components/pedido/FirmaSeccion.jsx'
import ImportesPedido from '../components/pedido/ImportesPedido.jsx'
import {
  infoEstadoFirma,
  infoEstadoPago,
  nombreRolFirmante,
  puedeFirmarPedido,
  puedePagarPedido,
} from '../pedidoEstado.js'

export default function PedidoDetalle() {
  const { id } = useParams()
  const { usuario } = useAuth()
  const [paramsBusqueda, setParamsBusqueda] = useSearchParams()
  const [pedido, setPedido] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [accionError, setAccionError] = useState(null)
  const [accionOk, setAccionOk] = useState(null)
  const [procesando, setProcesando] = useState(false)
  const [pestaña, setPestaña] = useState('resumen')

  const cargar = useCallback(async () => {
    setCargando(true)
    setError(null)
    try {
      const datos = await api.get(`/api/pedidos/${id}`)
      setPedido(datos)
    } catch (err) {
      setError(err.message)
      setPedido(null)
    } finally {
      setCargando(false)
    }
  }, [id])

  useEffect(() => { cargar() }, [cargar])

  // Avisos que llegan desde el carrito y enlace directo a la pestaña de firma.
  useEffect(() => {
    const creado = paramsBusqueda.get('creado')
    const errorEnvio = paramsBusqueda.get('errorEnvio')
    const pestañaSolicitada = paramsBusqueda.get('pestana')
    if (creado === 'borrador') setAccionOk('Pedido guardado como BORRADOR. Recuerda enviarlo a firma.')
    if (creado === 'firma') setAccionOk('Pedido enviado a firma correctamente.')
    if (errorEnvio) setAccionError(`El pedido se guardó como borrador, pero no se pudo enviar a firma: ${errorEnvio}`)
    if (pestañaSolicitada === 'firma') setPestaña('firma')
    if (creado || errorEnvio || pestañaSolicitada) {
      const siguientes = new URLSearchParams(paramsBusqueda)
      siguientes.delete('creado')
      siguientes.delete('errorEnvio')
      siguientes.delete('pestana')
      setParamsBusqueda(siguientes, { replace: true })
    }
    // Solo procesa los parámetros recibidos al abrir el detalle.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const estado = pedido?.estado
  const puedeFirmar = puedeFirmarPedido(pedido, usuario)
  const puedeEditarLineas = estado === 'BORRADOR'
  const puedePagar = puedePagarPedido(pedido, usuario)

  useEffect(() => {
    if (pestaña === 'firma' && pedido && !puedeFirmar) setPestaña('resumen')
  }, [pedido, puedeFirmar, pestaña])

  async function accion(fn, mensajeOk) {
    setProcesando(true)
    setAccionError(null)
    setAccionOk(null)
    try {
      await fn()
      setAccionOk(mensajeOk)
      await cargar()
      setTimeout(() => setAccionOk(null), 4000)
    } catch (err) {
      setAccionError(err.message)
    } finally {
      setProcesando(false)
    }
  }

  const enviarFirma = () =>
    accion(() => api.patch(`/api/pedidos/${id}/enviar-firma`), 'Pedido enviado a firma: se ha reservado el stock.')
  const cancelar = () => {
    if (!window.confirm('¿Seguro que quieres cancelar este pedido?')) return
    accion(() => api.patch(`/api/pedidos/${id}/cancelar`), 'Pedido cancelado.')
  }
  const alFirmar = async () => {
    // Tras la firma el pedido queda FIRMADO (pendiente de pago): se continúa en la pestaña de pago
    setPestaña('pago')
    setAccionError(null)
    setAccionOk('Pedido firmado correctamente. Continúa con el pago.')
    await cargar()
    setTimeout(() => setAccionOk(null), 4000)
  }

  if (cargando) return <div className="centrado-pagina"><Spinner texto="Cargando el pedido…" /></div>
  if (error) return (
    <section>
      <ErrorBox mensaje={error} onReintentar={cargar} />
      <p><Link className="btn btn-secundario" to="/mis-pedidos">← Volver a mis pedidos</Link></p>
    </section>
  )
  if (!pedido) return null

  return (
    <section>
      <p><Link to="/mis-pedidos" className="enlace-volver">← Volver a mis pedidos</Link></p>
      <div className="cabecera-pedido">
        <h1>Pedido #{pedido.id}</h1>
        <BadgeEstadoPedido estado={estado} />
      </div>
      {pedido.clinicaNombre && <p className="texto-suave">Clínica: {pedido.clinicaNombre}</p>}
      {pedido.veterinarioNombre && <p className="texto-suave">Responsable: {pedido.veterinarioNombre}{pedido.numeroColegiadoResponsable ? ` · Nº colegiado: ${pedido.numeroColegiadoResponsable}` : ''}</p>}
      {pedido.observaciones && <p className="texto-suave">Observaciones: {pedido.observaciones}</p>}

      <ErrorBox mensaje={accionError} />
      {accionOk && <div className="caja-ok">{accionOk}</div>}
      {procesando && <Spinner texto="Procesando la operación…" />}

      <div className="pestañas" role="tablist">
        <button role="tab" aria-selected={pestaña === 'resumen'} className={'pestaña' + (pestaña === 'resumen' ? ' activa' : '')} onClick={() => setPestaña('resumen')}>Resumen</button>
        {puedeFirmar && (
          <button role="tab" aria-selected={pestaña === 'firma'} className={'pestaña' + (pestaña === 'firma' ? ' activa' : '')} onClick={() => setPestaña('firma')}>Firmar</button>
        )}
        <button role="tab" aria-selected={pestaña === 'pago'} className={'pestaña' + (pestaña === 'pago' ? ' activa' : '')} onClick={() => setPestaña('pago')}>Pago</button>
        <button role="tab" aria-selected={pestaña === 'documentos'} className={'pestaña' + (pestaña === 'documentos' ? ' activa' : '')} onClick={() => setPestaña('documentos')}>Documentos</button>
      </div>

      {pestaña === 'resumen' && (
        <div className="tarjeta">
          <h2>Líneas del pedido</h2>
          <LineasPedido pedido={pedido} editable={puedeEditarLineas} onGuardado={cargar} />
          <ImportesPedido pedido={pedido} />

          <h2>Autorización y pago</h2>
          <EstadoAutorizacionPago pedido={pedido} />

          <h2>Cronología</h2>
          <CronologiaFechas pedido={pedido} />

          <h2>Acciones según el estado</h2>
          <AccionesPedido
            estado={estado}
            puedeFirmar={puedeFirmar}
            puedePagar={puedePagar}
            procesando={procesando}
            enviarFirma={enviarFirma}
            cancelar={cancelar}
            irAFirmar={() => setPestaña('firma')}
            irAPagar={() => setPestaña('pago')}
          />
        </div>
      )}

      {pestaña === 'firma' && puedeFirmar && (
        <FirmaSeccion
          pedido={pedido}
          onFirmado={alFirmar}
        />
      )}

      {pestaña === 'pago' && (
        <div className="tarjeta">
          <h2>Pago del pedido</h2>
          <PagoSeccion pedido={pedido} puedePagar={puedePagar} />
        </div>
      )}

      {pestaña === 'documentos' && (
        <div className="tarjeta">
          <h2>Documentos del pedido</h2>
          <DocumentosSeccion pedidoId={pedido.id} />
        </div>
      )}
    </section>
  )
}

function EstadoAutorizacionPago({ pedido }) {
  const firma = infoEstadoFirma(pedido)
  const pago = infoEstadoPago(pedido)
  return (
    <div className="estado-autorizacion-pago">
      <p><strong>Responsable:</strong> {pedido.veterinarioNombre || '—'}{pedido.numeroColegiadoResponsable ? ` · Nº colegiado: ${pedido.numeroColegiadoResponsable}` : ''}</p>
      <p>
        <strong>Firma:</strong> <span className={`etiqueta ${firma.clase}`}>{firma.texto}</span>
        {firma.texto === 'Firmada' && <> · Firmado por: <strong>{pedido.firmanteNombre || '—'}</strong> ({nombreRolFirmante(pedido.firmanteRol)})</>}
      </p>
      <p><strong>Pago:</strong> <span className={`etiqueta ${pago.clase}`}>{pago.texto}</span></p>
    </div>
  )
}

function AccionesPedido({ estado, puedeFirmar, puedePagar, procesando, enviarFirma, cancelar, irAFirmar, irAPagar }) {
  if (estado === 'BORRADOR') {
    return (
      <div className="acciones-fila">
        <button type="button" className="btn btn-primario" onClick={enviarFirma} disabled={procesando}>Enviar a firma</button>
        <button type="button" className="btn btn-peligro" onClick={cancelar} disabled={procesando}>Cancelar pedido</button>
      </div>
    )
  }
  if (estado === 'PENDIENTE_FIRMA') {
    return (
      <div className="acciones-fila">
        {puedeFirmar
          ? <button type="button" className="btn btn-primario" onClick={irAFirmar} disabled={procesando}>Revisar y firmar</button>
          : <p className="texto-suave">Pendiente de que el veterinario asignado lo revise y firme con su cuenta. Después se podrá pagar.</p>}
        <button type="button" className="btn btn-peligro" onClick={cancelar} disabled={procesando}>Cancelar pedido</button>
      </div>
    )
  }
  if (estado === 'FIRMADO') {
    return (
      <div className="acciones-fila">
        {puedePagar && <button type="button" className="btn btn-primario" onClick={irAPagar} disabled={procesando}>Pagar (entorno de pruebas)</button>}
        <button type="button" className="btn btn-peligro" onClick={cancelar} disabled={procesando}>Cancelar pedido</button>
      </div>
    )
  }
  if (estado === 'CANCELADO') return <p className="texto-suave">Este pedido está cancelado: no admite más acciones.</p>
  return <p className="texto-suave">El pedido está en preparación/logística: la distribuidora lo gestionará desde el panel de administración.</p>
}
