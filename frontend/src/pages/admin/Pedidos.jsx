import { useEffect, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { api } from '../../api.js'
import { dinero, fechaCorta, kg, ESTADOS_PEDIDO, NOMBRE_ESTADO_PEDIDO, totalPedido } from '../../format.js'
import Tabla from '../../components/Tabla.jsx'
import { ErrorBox } from '../../components/Spinner.jsx'
import { BadgeEstadoPedido } from '../../components/Badges.jsx'
import ListadoPorSalida from '../../components/pedido/ListadoPorSalida.jsx'
import { CLASIFICACIONES, SITUACIONES, infoEstadoFirma, infoEstadoPago } from '../../pedidoEstado.js'

function Etiqueta({ info }) {
  return <span className={`etiqueta ${info.clase}`}>{info.texto}</span>
}

// Bultos que exige el peso; los registros solo se mencionan si ya hay alguno y no coinciden
function CeldaBultos({ pedido: p }) {
  if (p.estado === 'CANCELADO') return <span className="texto-suave">—</span>
  if (p.bultosCalculados == null) {
    return <>{Number(p.pesoTotalKg) > 0 && <span>≥ {kg(p.pesoTotalKg)} kg</span>}<small className="error-campo">Faltan pesos</small></>
  }
  const registrados = p.bultos ?? 0
  return (
    <>
      <strong>{p.bultosCalculados}</strong>
      <small className="texto-suave">{kg(p.pesoTotalKg)} kg</small>
      {registrados > 0 && registrados !== p.bultosCalculados && <small className="error-campo">{registrados} registrados</small>}
    </>
  )
}

export default function AdminPedidos() {
  const [paramsUrl, setParamsUrl] = useSearchParams()
  const location = useLocation()
  const [pedidos, setPedidos] = useState(null)
  const [clinicas, setClinicas] = useState([])
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [busqueda, setBusqueda] = useState(paramsUrl.get('q') || '')
  const [estado, setEstado] = useState(paramsUrl.get('estado') || '')
  // Vista por firma y pago: la clasificación la calcula el backend (pedido.autorizacion)
  const [vista, setVista] = useState(paramsUrl.get('vista') || '')
  // Desde las tarjetas del resumen: ?situacion=FIRMA | PAGO | LISTO (misma regla que el contador)
  const [situacion, setSituacion] = useState(paramsUrl.get('situacion') || '')
  const [clinicaId, setClinicaId] = useState(paramsUrl.get('clinicaId') || '')
  const [salidas, setSalidas] = useState([])
  // Filtro por salida: id de la salida, 'SIN' = sin asignar, '' = todas
  const [salidaFiltro, setSalidaFiltro] = useState(paramsUrl.get('salida') || '')
  const [desde, setDesde] = useState(paramsUrl.get('desde') || '')
  const [hasta, setHasta] = useState(paramsUrl.get('hasta') || '')

  // Los filtros viven en la URL: al volver del detalle (o recargar) siguen aplicados
  useEffect(() => {
    const valores = { estado, vista, situacion, clinicaId, salida: salidaFiltro, desde, hasta, q: busqueda }
    setParamsUrl(Object.fromEntries(Object.entries(valores).filter(([, v]) => v)), { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [estado, vista, situacion, clinicaId, salidaFiltro, desde, hasta, busqueda])

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
    api.get('/api/destinos').then((d) => setSalidas(Array.isArray(d) ? d : [])).catch(() => {})
  }, [])

  // Filtro por fecha de creación: las fechas llegan en ISO y se compara el día (AAAA-MM-DD)
  const contar = (clave) => (pedidos || []).filter((p) => p.autorizacion?.clasificacion === clave).length

  const filasFiltradas = (pedidos || []).filter((p) => !vista || vista === 'POR_SALIDA' || p.autorizacion?.clasificacion === vista)
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
      <h1 className="no-imprimir">Pedidos (administración)</h1>

      <div className="vistas-pedidos no-imprimir" role="group" aria-label="Separar pedidos por firma y pago">
        <button type="button" className={'btn ' + (vista === '' ? 'btn-primario' : 'btn-secundario')} aria-pressed={vista === ''} onClick={() => setVista('')}>
          Todos ({(pedidos || []).length})
        </button>
        {Object.entries(CLASIFICACIONES).map(([clave, texto]) => (
          <button key={clave} type="button" className={'btn ' + (vista === clave ? 'btn-primario' : 'btn-secundario')}
            aria-pressed={vista === clave} onClick={() => setVista(clave)}>
            {texto} ({contar(clave)})
          </button>
        ))}
        <button type="button" className={'btn ' + (vista === 'POR_SALIDA' ? 'btn-primario' : 'btn-secundario')}
          aria-pressed={vista === 'POR_SALIDA'} onClick={() => setVista('POR_SALIDA')}>
          Por salida
        </button>
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
      {vista !== 'POR_SALIDA' && (
        <>
          <p className="texto-suave">Busca pedidos por número, clínica, fecha y estado; consulta su firma y su pago y entra a gestionarlos.</p>
    
          <div className="filtros-panel">
            <label className="campo">
              <span>Creado desde</span>
              <input className="input" type="date" value={desde} max={hasta || undefined} onChange={(e) => setDesde(e.target.value)} />
            </label>
            <label className="campo">
              <span>Hasta</span>
              <input className="input" type="date" value={hasta} min={desde || undefined} onChange={(e) => setHasta(e.target.value)} />
            </label>
          </div>
        </>
      )}

      <ErrorBox mensaje={error} onReintentar={cargar} />

      {!error && vista === 'POR_SALIDA' && <ListadoPorSalida pedidos={pedidos || []} cargando={cargando} />}

      {!error && vista !== 'POR_SALIDA' && (
        <Tabla
          columns={[
            { key: 'id', label: 'Nº', render: (p) => <Link to={`/admin/pedidos/${p.id}`} state={{ volver: location.search }}>#{p.id}</Link> },
            {
              key: 'clinicaNombre', label: 'Clínica', render: (p) => (
                <>
                  {p.clinicaNombre}
                  <small className="texto-suave">Responsable: {p.veterinarioNombre}</small>
                  <small className="texto-suave">Creado por: {p.creadoPorNombre}</small>
                </>
              ),
            },
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
            { key: 'salida', label: 'Salida', render: (p) => p.salida || <span className="texto-suave">{p.estado === 'CANCELADO' ? '—' : 'Sin asignar'}</span> },
            // Peso y bultos los calcula el servidor (máx. 25 kg por bulto); aquí solo se muestran
            { key: 'bultos', label: 'Bultos', render: (p) => <CeldaBultos pedido={p} /> },
            { key: 'total', label: 'Total', render: (p) => (totalPedido(p) != null ? dinero(totalPedido(p)) : '—') },
            { key: 'gestion', label: '', render: (p) => <Link className="btn btn-mini btn-secundario" to={`/admin/pedidos/${p.id}`} state={{ volver: location.search }}>Gestionar</Link> },
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
              etiqueta: 'Salida',
              valor: salidaFiltro,
              setValor: setSalidaFiltro,
              opciones: [
                { value: '', label: 'Todas las salidas' },
                { value: 'SIN', label: 'Sin asignar' },
                ...salidas.map((d) => ({ value: String(d.id), label: d.etiqueta })),
              ],
              cumple: (p, v) => (v === 'SIN' ? !p.salidaId : String(p.salidaId) === v),
            },
            {
              etiqueta: 'Clínica',
              valor: clinicaId,
              setValor: setClinicaId,
              opciones: [{ value: '', label: 'Todas las clínicas' }, ...clinicas.map((c) => ({ value: String(c.id), label: c.nombre }))],
              cumple: (p, v) => String(p.clinicaId) === v,
            },
          ]}
          textoVacio="Todavía no hay pedidos."
          filtrosExternosActivos={Boolean(vista || situacion || desde || hasta)}
          onLimpiarExternos={() => { setVista(''); setSituacion(''); setDesde(''); setHasta('') }}
          cargando={cargando}
        />
      )}
    </section>
  )
}
