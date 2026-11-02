import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../../api.js'
import { dinero, fechaCorta, ESTADOS_PEDIDO, NOMBRE_ESTADO_PEDIDO, totalPedido } from '../../format.js'
import Tabla from '../../components/Tabla.jsx'
import { ErrorBox } from '../../components/Spinner.jsx'
import { BadgeEstadoPedido } from '../../components/Badges.jsx'
import { CLASIFICACIONES, SITUACIONES, infoEstadoFirma, infoEstadoPago } from '../../pedidoEstado.js'

function Etiqueta({ info }) {
  return <span className={`etiqueta ${info.clase}`}>{info.texto}</span>
}

export default function AdminPedidos() {
  const [paramsUrl] = useSearchParams()
  const [pedidos, setPedidos] = useState(null)
  const [clinicas, setClinicas] = useState([])
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [busqueda, setBusqueda] = useState('')
  const [estado, setEstado] = useState(paramsUrl.get('estado') || '')
  // Vista por firma y pago: la clasificación la calcula el backend (pedido.autorizacion)
  const [vista, setVista] = useState(paramsUrl.get('vista') || '')
  // Desde las tarjetas del resumen: ?situacion=FIRMA | PAGO | LISTO (misma regla que el contador)
  const [situacion, setSituacion] = useState(paramsUrl.get('situacion') || '')
  const [clinicaId, setClinicaId] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      if (estado) params.set('estado', estado)
      if (clinicaId) params.set('clinicaId', clinicaId)
      const datos = await api.get('/api/pedidos' + (params.toString() ? `?${params}` : ''))
      setPedidos(Array.isArray(datos) ? datos : [])
    } catch (err) {
      setError(err.message)
      setPedidos(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargar() }, [estado, clinicaId])
  useEffect(() => {
    api.get('/api/clinicas').then((c) => setClinicas(Array.isArray(c) ? c : [])).catch(() => {})
  }, [])

  // Filtro por fecha de creación: las fechas llegan en ISO y se compara el día (AAAA-MM-DD)
  const contar = (clave) => (pedidos || []).filter((p) => p.autorizacion?.clasificacion === clave).length

  const filasFiltradas = (pedidos || []).filter((p) => !vista || p.autorizacion?.clasificacion === vista)
    .filter((p) => !situacion || (SITUACIONES[situacion]?.cumple(p) ?? true)).filter((p) => {
    const dia = (p.fechaCreacion || '').slice(0, 10)
    return (!desde || dia >= desde) && (!hasta || dia <= hasta)
  })

  const opcionesEstado = [
    { value: '', label: 'Todos los estados' },
    ...Object.keys(ESTADOS_PEDIDO).map((e) => ({ value: e, label: NOMBRE_ESTADO_PEDIDO(e) })),
  ]

  return (
    <section>
      <h1>Pedidos (administración)</h1>

      <div className="vistas-pedidos" role="group" aria-label="Separar pedidos por firma y pago">
        <button type="button" className={'btn ' + (vista === '' ? 'btn-primario' : 'btn-secundario')} aria-pressed={vista === ''} onClick={() => setVista('')}>
          Todos ({(pedidos || []).length})
        </button>
        {Object.entries(CLASIFICACIONES).map(([clave, texto]) => (
          <button key={clave} type="button" className={'btn ' + (vista === clave ? 'btn-primario' : 'btn-secundario')}
            aria-pressed={vista === clave} onClick={() => setVista(clave)}>
            {texto} ({contar(clave)})
          </button>
        ))}
      </div>
      {situacion && SITUACIONES[situacion] && (
        <p className="caja-aviso">
          Mostrando: <strong>{SITUACIONES[situacion].texto}</strong>{' '}
          <button type="button" className="btn btn-mini btn-secundario" onClick={() => setSituacion('')}>Quitar filtro</button>
        </p>
      )}
      <p className="texto-suave">
        {vista === 'PAGADO_PENDIENTE_FIRMA' && 'Pago completo confirmado, pero falta la firma válida del veterinario asignado: no se pueden preparar. La columna «Firma» indica qué falta.'}
        {vista === 'FIRMADO_Y_PAGADO' && 'Firma válida del veterinario asignado y pago completo. Solo los marcados «Listo para preparar» pueden empezar la preparación.'}
        {vista === 'OTROS' && 'Borradores, pedidos sin pago completo y cancelados.'}
      </p>
      <p className="texto-suave">Busca pedidos por número, clínica, fecha y estado; consulta su firma y su pago y entra a gestionarlos.</p>

      <div className="filtros-panel">
        <label className="campo">
          <span>Creado desde</span>
          <input type="date" value={desde} max={hasta || undefined} onChange={(e) => setDesde(e.target.value)} />
        </label>
        <label className="campo">
          <span>Hasta</span>
          <input type="date" value={hasta} min={desde || undefined} onChange={(e) => setHasta(e.target.value)} />
        </label>
        {(desde || hasta) && (
          <button type="button" className="btn btn-secundario btn-mini" onClick={() => { setDesde(''); setHasta('') }}>
            Quitar fechas
          </button>
        )}
      </div>

      <ErrorBox mensaje={error} onReintentar={cargar} />

      {!error && (
        <Tabla
          columns={[
            { key: 'id', label: 'Nº', render: (p) => <Link to={`/admin/pedidos/${p.id}`}>#{p.id}</Link> },
            { key: 'clinicaNombre', label: 'Clínica' },
            { key: 'veterinarioNombre', label: 'Veterinario', soloEscritorio: true },
            { key: 'creadoPorNombre', label: 'Creado por', soloEscritorio: true },
            { key: 'fechaCreacion', label: 'Fecha', render: (p) => fechaCorta(p.fechaCreacion) },
            { key: 'estado', label: 'Estado', render: (p) => <BadgeEstadoPedido estado={p.estado} /> },
            {
              key: 'firma', label: 'Firma', render: (p) => (
                <>
                  <Etiqueta info={infoEstadoFirma(p)} />
                  {p.autorizacion?.motivoFirma && p.autorizacion.clasificacion === 'PAGADO_PENDIENTE_FIRMA' && <small className="texto-suave">{p.autorizacion.motivoFirma}</small>}
                </>
              ),
            },
            {
              key: 'pago', label: 'Pago', render: (p) => (
                <>
                  <Etiqueta info={infoEstadoPago(p)} />
                  {p.devolucionPendiente && <small className="error-campo">Devolución pendiente</small>}
                  {p.autorizacion?.listoParaPreparar && <small><strong>Listo para preparar</strong></small>}
                </>
              ),
            },
            { key: 'total', label: 'Total', render: (p) => (totalPedido(p) != null ? dinero(totalPedido(p)) : '—') },
            { key: 'gestion', label: '', render: (p) => <Link className="btn btn-mini btn-secundario" to={`/admin/pedidos/${p.id}`}>Gestionar</Link> },
          ]}
          filas={filasFiltradas}
          claveFila={(p) => p.id}
          busquedaCampos={['clinicaNombre', 'veterinarioNombre', 'creadoPorNombre', 'observaciones', (p) => p.id]}
          busquedaValor={busqueda}
          onBusqueda={setBusqueda}
          placeholderBusqueda="Buscar por clínica, veterinario, nº…"
          filtros={[
            { etiqueta: 'Estado', valor: estado, setValor: setEstado, opciones: opcionesEstado, cumple: (p, v) => p.estado === v },
            {
              etiqueta: 'Clínica',
              valor: clinicaId,
              setValor: setClinicaId,
              opciones: [{ value: '', label: 'Todas las clínicas' }, ...clinicas.map((c) => ({ value: String(c.id), label: c.nombre }))],
              cumple: (p, v) => String(p.clinicaId) === v,
            },
          ]}
          textoVacio="No hay pedidos que coincidan con la búsqueda."
          cargando={cargando}
        />
      )}
    </section>
  )
}
