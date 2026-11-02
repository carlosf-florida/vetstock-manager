import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../../api.js'
import { fecha } from '../../format.js'
import { Spinner, ErrorBox } from '../../components/Spinner.jsx'
import { BadgeEstadoPedido, BadgeEstadoPago } from '../../components/Badges.jsx'
import LineasPedido from '../../components/pedido/LineasPedido.jsx'
import ImportesPedido from '../../components/pedido/ImportesPedido.jsx'
import CronologiaFechas from '../../components/pedido/CronologiaFechas.jsx'
import DocumentosSeccion from '../../components/pedido/DocumentosSeccion.jsx'
import PagoSeccion from '../../components/pedido/PagoSeccion.jsx'
import {
  estadoFirmaPedido,
  estadoPagoPedido,
  infoEstadoFirma,
  infoEstadoPago,
  nombreRolFirmante,
} from '../../pedidoEstado.js'

export default function AdminPedidoDetalle() {
  const { id } = useParams()
  const [pedido, setPedido] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [accionError, setAccionError] = useState(null)
  const [accionOk, setAccionOk] = useState(null)
  const [procesando, setProcesando] = useState(false)
  const [imagenFirma, setImagenFirma] = useState(null)
  const [errorFirma, setErrorFirma] = useState(null)

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

  // Imagen de la firma, si existe, independientemente del estado logístico.
  useEffect(() => {
    setImagenFirma(null)
    setErrorFirma(null)
    if (!pedido || estadoFirmaPedido(pedido) !== 'FIRMADO') return
    let objetoUrl = null
    api.blob(`/api/pedidos/${pedido.id}/firma/imagen`)
      .then((blob) => {
        objetoUrl = URL.createObjectURL(blob)
        setImagenFirma(objetoUrl)
      })
      .catch((err) => {
        if (err.status !== 404) setErrorFirma('No se ha podido cargar la imagen de la firma: ' + err.message)
      })
    return () => { if (objetoUrl) URL.revokeObjectURL(objetoUrl) }
  }, [pedido])

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

  const enviarFirma = () => accion(() => api.patch(`/api/pedidos/${id}/enviar-firma`), 'Pedido enviado a firma: stock reservado.')
  const cancelar = () => {
    if (!window.confirm('¿Seguro que quieres cancelar este pedido? Se liberará el stock reservado.')) return
    accion(() => api.patch(`/api/pedidos/${id}/cancelar`), 'Pedido cancelado.')
  }
  const iniciarPreparacion = () => accion(() => api.patch(`/api/pedidos/${id}/iniciar-preparacion`), 'Preparación iniciada.')
  const preparar = () => accion(() => api.patch(`/api/pedidos/${id}/preparar`), 'Pedido marcado como PREPARADO.')
  const expedir = () => {
    if (!window.confirm('Al expedir se descontará el stock físico de forma definitiva. ¿Continuar?')) return
    accion(() => api.patch(`/api/pedidos/${id}/expedir`), 'Pedido expedido: stock físico descontado.')
  }

  async function exportarOdoo() {
    setProcesando(true)
    setAccionError(null)
    setAccionOk(null)
    try {
      const resultado = await api.post(`/api/odoo/pedidos/${id}/exportar`, {})
      await cargar()
      if (resultado?.exito === false) {
        setAccionError(resultado.detalle || 'No se ha podido exportar el pedido a Odoo.')
        return
      }
      setAccionOk(resultado?.detalle || 'Pedido exportado o actualizado en Odoo.')
      setTimeout(() => setAccionOk(null), 4000)
    } catch (err) {
      setAccionError(err.message)
    } finally {
      setProcesando(false)
    }
  }

  if (cargando) return <div className="centrado-pagina"><Spinner texto="Cargando el pedido…" /></div>
  if (error) return (
    <section>
      <ErrorBox mensaje={error} onReintentar={cargar} />
      <p><Link className="btn btn-secundario" to="/admin/pedidos">← Volver a pedidos</Link></p>
    </section>
  )
  if (!pedido) return null

  const estado = pedido.estado
  const firma = infoEstadoFirma(pedido)
  const pago = infoEstadoPago(pedido)
  const puedeExportarOdoo = estadoPagoPedido(pedido) === 'PAGADO'

  return (
    <section>
      <p><Link to="/admin/pedidos" className="enlace-volver">← Volver a pedidos</Link></p>
      <div className="cabecera-pedido">
        <h1>Pedido #{pedido.id}</h1>
        <BadgeEstadoPedido estado={estado} />
      </div>
      <p className="texto-suave">
        {pedido.clinicaNombre && <>Clínica: <strong>{pedido.clinicaNombre}</strong> · </>}
        {pedido.veterinarioNombre && <>Responsable: {pedido.veterinarioNombre} · </>}
        {pedido.creadoPorNombre && <>Creado por: {pedido.creadoPorNombre}</>}
        {pedido.modificadoPorNombre && <> · Modificado por: {pedido.modificadoPorNombre} ({fecha(pedido.fechaModificacion)})</>}
      </p>
      {pedido.observaciones && <p className="texto-suave">Observaciones: {pedido.observaciones}</p>}

      <ErrorBox mensaje={accionError} />
      {accionOk && <div className="caja-ok">{accionOk}</div>}
      {procesando && <Spinner texto="Procesando la operación…" />}

      <div className="tarjeta">
        <h2>Acciones del almacén</h2>
        <AccionesAdmin
          estado={estado}
          autorizacion={pedido.autorizacion}
          pagoPagado={estadoPagoPedido(pedido) === 'PAGADO'}
          procesando={procesando}
          enviarFirma={enviarFirma}
          cancelar={cancelar}
          iniciarPreparacion={iniciarPreparacion}
          preparar={preparar}
          expedir={expedir}
        />
      </div>

      <div className="tarjeta">
        <h2>Líneas del pedido</h2>
        <LineasPedido pedido={pedido} editable={estado === 'BORRADOR'} onGuardado={cargar} />
        <div className="totales-pedido">
          <ImportesPedido pedido={pedido} />
        </div>
      </div>

      <div className="tarjeta">
        <h2>Firma</h2>
        <div className="estado-autorizacion-pago">
          <p><strong>Responsable:</strong> {pedido.veterinarioNombre || '—'}{pedido.numeroColegiadoResponsable ? ` · Nº colegiado: ${pedido.numeroColegiadoResponsable}` : ''}</p>
          <p><strong>Estado:</strong> <span className={`etiqueta ${firma.clase}`}>{firma.texto}</span></p>
          {pedido.autorizacion?.motivoFirma && <p className="error-campo">{pedido.autorizacion.motivoFirma}</p>}
          {pedido.devolucionPendiente && (
            <p className="caja-aviso">Rechazado tras el pago: <strong>devolución pendiente</strong> (se gestiona fuera de la aplicación). {pedido.motivoCancelacion}</p>
          )}
        </div>
        {estadoFirmaPedido(pedido) === 'FIRMADO' ? (
          <div className="firma-resumen">
            {imagenFirma ? (
              <img src={imagenFirma} alt="Imagen de la firma del pedido" className="firma-imagen" />
            ) : (
              <p className="texto-suave">{errorFirma || 'No hay imagen de firma disponible para este pedido.'}</p>
            )}
            <p className="texto-suave">
              Firmado por: <strong>{pedido.firmanteNombre || '—'}</strong> ({nombreRolFirmante(pedido.firmanteRol)})
              {pedido.fechaFirma && <> · Fecha de firma: {fecha(pedido.fechaFirma)}</>}
            </p>
          </div>
        ) : estado === 'PENDIENTE_FIRMA' ? (
          <p className="texto-suave">Esperando la firma del veterinario asignado.</p>
        ) : (
          <p className="texto-suave">Este pedido aún no ha llegado al proceso de firma.</p>
        )}
      </div>

      <div className="tarjeta">
        <h2>Pago</h2>
        <p><span className={`etiqueta ${pago.clase}`}>{pago.texto}</span></p>
        <PagoSeccion pedido={pedido} puedePagar={false} />
        <p className="texto-suave">El pago lo realiza la clínica (antes o después de la firma) y lo confirma la notificación del proveedor. Administración no puede marcarlo como pagado.</p>
      </div>

      <div className="tarjeta">
        <h2>Odoo</h2>
        {pedido.odooId && <p className="texto-suave">Pedido vinculado en Odoo: <strong>#{pedido.odooId}</strong>. Una nueva exportación actualiza ese mismo pedido.</p>}
        {pedido.odooSincronizacionPendiente && <p className="error-campo">La sincronización con Odoo está pendiente{pedido.odooUltimoError ? `: ${pedido.odooUltimoError}` : '.'}</p>}
        {!pedido.odooId && !pedido.odooSincronizacionPendiente && <p className="texto-suave">Todavía no hay exportación registrada para este pedido.</p>}
        {puedeExportarOdoo ? (
          <button type="button" className="btn btn-secundario" onClick={exportarOdoo} disabled={procesando}>
            {pedido.odooId ? 'Actualizar en Odoo' : pedido.odooSincronizacionPendiente ? 'Reintentar exportación en Odoo' : 'Exportar a Odoo'}
          </button>
        ) : (
          <p className="texto-suave">La exportación manual estará disponible cuando el pago esté confirmado.</p>
        )}
      </div>

      <div className="tarjeta">
        <h2>Documentos (pedidos, facturas, albaranes)</h2>
        <DocumentosSeccion pedidoId={pedido.id} puedeSubir />
      </div>

      <div className="tarjeta">
        <h2>Cronología</h2>
        <CronologiaFechas pedido={pedido} />
        {pedido.pago && <p><BadgeEstadoPago estado={pedido.pago.estado} /></p>}
      </div>
    </section>
  )
}

function AccionesAdmin({ estado, autorizacion, pagoPagado, procesando, enviarFirma, cancelar, iniciarPreparacion, preparar, expedir }) {
  const firmaOk = Boolean(autorizacion?.firmaValida)
  // Preparar exige firma válida del veterinario asignado + pago completo (el backend lo vuelve a comprobar)
  const listo = Boolean(autorizacion?.listoParaPreparar)
  return (
    <div className="acciones-fila acciones-envueltas">
      {estado === 'BORRADOR' && (
        <button type="button" className="btn btn-primario" onClick={enviarFirma} disabled={procesando}>Enviar a firma</button>
      )}
      {['PENDIENTE_FIRMA', 'FIRMADO', 'LISTO_PREPARAR'].includes(estado) && pagoPagado && !firmaOk && (
        <span className="etiqueta est-pendiente-firma">Pagado, pero sin firma válida: no se puede preparar</span>
      )}
      {estado === 'FIRMADO' && !pagoPagado && firmaOk && (
        <span className="etiqueta est-firmado">Esperando pago: no se puede preparar todavía</span>
      )}
      {estado === 'LISTO_PREPARAR' && listo && (
        <button type="button" className="btn btn-primario" onClick={iniciarPreparacion} disabled={procesando}>Iniciar preparación</button>
      )}
      {estado === 'EN_PREPARACION' && (
        <button type="button" className="btn btn-primario" onClick={preparar} disabled={procesando}>Marcar como preparado</button>
      )}
      {estado === 'PREPARADO' && (
        <button type="button" className="btn btn-primario" onClick={expedir} disabled={procesando}>Expedir</button>
      )}
      {['BORRADOR', 'PENDIENTE_FIRMA', 'FIRMADO'].includes(estado) && !pagoPagado && (
        <button type="button" className="btn btn-peligro" onClick={cancelar} disabled={procesando}>Cancelar pedido</button>
      )}
      {estado === 'CANCELADO' && <span className="etiqueta est-cancelado">Pedido cancelado</span>}
      {['EN_PREPARACION', 'PREPARADO', 'EXPEDIDO'].includes(estado) && (
        <span className="texto-suave">
          {estado === 'EN_PREPARACION' && 'Usa «Marcar como preparado» cuando el pedido esté embalado.'}
          {estado === 'PREPARADO' && 'Usa «Expedir» para descontar el stock físico y registrar la salida.'}
          {estado === 'EXPEDIDO' && 'Pedido expedido: registra sus bultos en la sección Bultos.'}
        </span>
      )}
    </div>
  )
}
