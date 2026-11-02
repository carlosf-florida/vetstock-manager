import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { dinero, fechaCorta, ESTADOS_PEDIDO, NOMBRE_ESTADO_PEDIDO } from '../format.js'
import { totalPedido } from '../format.js'
import Tabla from '../components/Tabla.jsx'
import { BadgeEstadoPedido } from '../components/Badges.jsx'
import { ErrorBox } from '../components/Spinner.jsx'
import { infoEstadoFirma, infoEstadoPago, necesitaFirma, puedeFirmarPedido, SITUACIONES } from '../pedidoEstado.js'

export default function MisPedidos() {
  const { usuario } = useAuth()
  const [pedidos, setPedidos] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [busqueda, setBusqueda] = useState('')
  const [paramsUrl] = useSearchParams()
  // Desde las tarjetas del inicio se llega con ?situacion=FIRMA | PAGO | PREPARACION
  const [estado, setEstado] = useState(paramsUrl.get('estado') || '')
  const [situacion, setSituacion] = useState(paramsUrl.get('situacion') || '')

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      if (estado) params.set('estado', estado)
      const datos = await api.get('/api/pedidos' + (params.toString() ? `?${params}` : ''))
      setPedidos(Array.isArray(datos) ? datos : [])
    } catch (err) {
      setError(err.message)
      setPedidos(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => { cargar() }, [estado])

  const opcionesEstado = [
    { value: '', label: 'Todos los estados' },
    ...Object.keys(ESTADOS_PEDIDO).map((e) => ({ value: e, label: NOMBRE_ESTADO_PEDIDO(e) })),
  ]

  return (
    <section>
      <h1>Mis pedidos</h1>
      <p className="texto-suave">
        Todos los pedidos de tu clínica, los haya hecho quien los haya hecho. Firma siempre el veterinario
        responsable; la preparación solo empieza con su firma válida y el pago completo.
      </p>

      <ErrorBox mensaje={error} onReintentar={cargar} />

      {!error && (
        <Tabla
          columns={[
            { key: 'id', label: 'Nº pedido', render: (p) => <Link to={`/pedidos/${p.id}`}>#{p.id}</Link> },
            { key: 'fechaCreacion', label: 'Fecha', render: (p) => fechaCorta(p.fechaCreacion || p.fecha) },
            { key: 'creadoPorNombre', label: 'Realizado por', soloEscritorio: true },
            { key: 'veterinarioNombre', label: 'Responsable de firma', soloEscritorio: true },
            {
              key: 'estado', label: 'Estado', render: (p) => (
                <>
                  <BadgeEstadoPedido estado={p.estado} />
                  {/* Pedidos antiguos con una firma que no vale: no se preparan hasta que firme el veterinario */}
                  {p.estado === 'LISTO_PREPARAR' && necesitaFirma(p) && <small className="error-campo">Bloqueado: falta la firma del veterinario</small>}
                </>
              ),
            },
            {
              key: 'firma',
              label: 'Firma',
              render: (p) => {
                const firma = infoEstadoFirma(p)
                return (
                  <div className="estado-tabla">
                    <span className={`etiqueta ${firma.clase}`}>{firma.texto}</span>
                    {puedeFirmarPedido(p, usuario) && <Link to={`/pedidos/${p.id}?pestana=firma`}>Firmar</Link>}
                  </div>
                )
              },
            },
            { key: 'pago', label: 'Pago', render: (p) => {
              const pago = infoEstadoPago(p)
              return <span className={`etiqueta ${pago.clase}`}>{pago.texto}</span>
            } },
            { key: 'total', label: 'Total', render: (p) => (totalPedido(p) != null ? dinero(totalPedido(p)) : '—') },
            { key: 'ver', label: '', render: (p) => <Link className="btn btn-mini btn-secundario" to={`/pedidos/${p.id}`}>Ver detalle</Link> },
          ]}
          filas={pedidos || []}
          claveFila={(p) => p.id}
          busquedaCampos={['id', 'estado', 'observaciones', 'firmanteNombre', 'veterinarioNombre', 'creadoPorNombre']}
          busquedaValor={busqueda}
          onBusqueda={setBusqueda}
          placeholderBusqueda="Buscar por número u observaciones…"
          filtros={[
            { etiqueta: 'Estado', valor: estado, setValor: setEstado, opciones: opcionesEstado, cumple: (p, v) => p.estado === v },
            {
              etiqueta: 'Situación',
              valor: situacion,
              setValor: setSituacion,
              opciones: [
                { value: '', label: 'Todas' },
                ...Object.entries(SITUACIONES).map(([clave, s]) => ({ value: clave, label: s.texto })),
              ],
              cumple: (p, v) => SITUACIONES[v]?.cumple(p) ?? true,
            },
          ]}
          textoVacio="Todavía no tienes pedidos, o ninguno coincide con el filtro."
          cargando={cargando}
        />
      )}
    </section>
  )
}
