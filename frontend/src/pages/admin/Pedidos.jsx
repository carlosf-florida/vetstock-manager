import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../../api.js'
import { dinero, fechaCorta, ESTADOS_PEDIDO, NOMBRE_ESTADO_PEDIDO, totalPedido } from '../../format.js'
import Tabla from '../../components/Tabla.jsx'
import { ErrorBox } from '../../components/Spinner.jsx'
import { BadgeEstadoPedido } from '../../components/Badges.jsx'

export default function AdminPedidos() {
  const [paramsUrl] = useSearchParams()
  const [pedidos, setPedidos] = useState(null)
  const [clinicas, setClinicas] = useState([])
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [busqueda, setBusqueda] = useState('')
  const [estado, setEstado] = useState(paramsUrl.get('estado') || '')
  const [clinicaId, setClinicaId] = useState('')

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

  const opcionesEstado = [
    { value: '', label: 'Todos los estados' },
    ...Object.keys(ESTADOS_PEDIDO).map((e) => ({ value: e, label: NOMBRE_ESTADO_PEDIDO(e) })),
  ]

  return (
    <section>
      <h1>Pedidos (administración)</h1>
      <p className="texto-suave">Busca pedidos por estado y clínica y entra a gestionarlos.</p>

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
            { key: 'total', label: 'Total', render: (p) => (totalPedido(p) != null ? dinero(totalPedido(p)) : '—') },
            { key: 'gestion', label: '', render: (p) => <Link className="btn btn-mini btn-secundario" to={`/admin/pedidos/${p.id}`}>Gestionar</Link> },
          ]}
          filas={pedidos || []}
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
