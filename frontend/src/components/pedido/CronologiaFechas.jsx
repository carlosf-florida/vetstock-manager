import { fecha } from '../../format.js'

const NOMBRES = {
  fechaCreacion: 'Creación del pedido',
  fecha: 'Creación del pedido',
  fechaEnvioFirma: 'Envío a firma',
  fechaEnviadoFirma: 'Envío a firma',
  fechaPendienteFirma: 'Envío a firma',
  fechaFirma: 'Firma',
  fechaPago: 'Pago',
  fechaListoPreparar: 'Listo para preparar',
  fechaInicioPreparacion: 'Inicio de preparación',
  fechaEnPreparacion: 'Inicio de preparación',
  fechaPreparado: 'Preparado',
  fechaExpedicion: 'Expedición',
  fechaExpedido: 'Expedición',
  fechaCancelacion: 'Cancelación',
  fechaCancelado: 'Cancelación',
}

// Muestra todas las propiedades fecha* que traiga el pedido, en orden cronológico.
export default function CronologiaFechas({ pedido }) {
  const hitos = Object.keys(pedido)
    .filter((k) => /^fecha/i.test(k) && pedido[k])
    .map((k) => ({ clave: k, titulo: NOMBRES[k] || k, valor: pedido[k] }))
    .sort((a, b) => new Date(a.valor) - new Date(b.valor))

  if (hitos.length === 0) return <p className="texto-suave">Aún no hay fechas registradas para este pedido.</p>

  return (
    <ol className="cronologia">
      {hitos.map((h) => (
        <li key={h.clave}>
          <span className="cronologia-punto" aria-hidden="true"></span>
          <strong>{h.titulo}</strong>
          <span className="texto-suave">{fecha(h.valor)}</span>
        </li>
      ))}
    </ol>
  )
}
