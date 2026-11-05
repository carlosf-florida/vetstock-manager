import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../../api.js'
import { useNotificaciones } from '../../components/Notificaciones.jsx'
import { dinero, kg } from '../../format.js'
import Tabla from '../../components/Tabla.jsx'
import { ErrorBox, Spinner } from '../../components/Spinner.jsx'
import { BadgeActivo, BadgeStockBajo } from '../../components/Badges.jsx'

const FORMULARIO_VACIO = {
  codigo: '', nombre: '', descripcion: '', categoria: '', precio: '', pesoUnitarioKg: '', stockInicial: '0', stockMinimo: '0',
}

export default function AdminProductos() {
  const [paramsBusqueda] = useSearchParams()
  const [productos, setProductos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [soloBajo, setSoloBajo] = useState(paramsBusqueda.get('bajoStock') === '1')
  const [form, setForm] = useState(FORMULARIO_VACIO)
  const [editando, setEditando] = useState(null) // producto en edición
  const [errores, setErrores] = useState({})
  const [errorForm, setErrorForm] = useState(null)
  const { notificar } = useNotificaciones()
  const [guardando, setGuardando] = useState(false)

  async function cargar() {
    setCargando(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      if (busqueda.trim()) params.set('busqueda', busqueda.trim())
      if (soloBajo) params.set('soloBajoStock', 'true')
      const datos = await api.get('/api/productos' + (params.toString() ? `?${params}` : ''))
      setProductos(Array.isArray(datos) ? datos : [])
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    const t = setTimeout(cargar, 250)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [busqueda, soloBajo])

  function avisar(texto) {
    notificar({ texto })
  }

  function validar(datos) {
    const errs = {}
    if (!datos.codigo.trim()) errs.codigo = 'El código es obligatorio.'
    if (!datos.nombre.trim()) errs.nombre = 'El nombre es obligatorio.'
    const precio = Number(datos.precio)
    if (datos.precio === '' || !Number.isFinite(precio) || precio <= 0) errs.precio = 'El precio debe ser un número mayor que 0.'
    // Peso opcional (vacío = desconocido, nunca 0): kg con hasta 3 decimales (gramos)
    const peso = datos.pesoUnitarioKg.trim().replace(',', '.')
    if (peso && (!/^\d{1,7}(\.\d{1,3})?$/.test(peso) || Number(peso) <= 0)) {
      errs.pesoUnitarioKg = 'Peso en kg mayor que 0, con 3 decimales como mucho (por ejemplo 0,250).'
    }
    for (const campo of ['stockInicial', 'stockMinimo']) {
      const v = Number(datos[campo])
      if (!Number.isInteger(v) || v < 0) errs[campo] = 'Debe ser un número entero igual o mayor que 0.'
    }
    return errs
  }

  async function enviar(e) {
    e.preventDefault()
    const errs = validar(editando || form)
    setErrores(errs)
    if (Object.keys(errs).length) return
    setGuardando(true)
    setErrorForm(null)
    const destino = editando || form
    const cuerpo = {
      codigo: destino.codigo.trim(),
      nombre: destino.nombre.trim(),
      descripcion: destino.descripcion.trim() || null,
      categoria: destino.categoria.trim() || null,
      precio: Number(destino.precio),
      pesoUnitarioKg: destino.pesoUnitarioKg.trim() ? Number(destino.pesoUnitarioKg.trim().replace(',', '.')) : null,
      stockMinimo: Number(destino.stockMinimo),
    }
    try {
      if (editando) {
        await api.put(`/api/productos/${editando.id}`, cuerpo)
        avisar('Producto actualizado.')
      } else {
        cuerpo.stockInicial = Number(destino.stockInicial)
        await api.post('/api/productos', cuerpo)
        avisar('Producto creado.')
      }
      setForm(FORMULARIO_VACIO)
      setEditando(null)
      cargar()
    } catch (err) {
      setErrorForm(err.message)
    } finally {
      setGuardando(false)
    }
  }

  async function alternarActivo(p) {
    try {
      await api.patch(`/api/productos/${p.id}/${p.activo === false ? 'activar' : 'desactivar'}`)
      avisar(p.activo === false ? 'Producto activado.' : 'Producto desactivado.')
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  function empezarEdicion(p) {
    setEditando({
      id: p.id,
      codigo: p.codigo || '',
      nombre: p.nombre || '',
      descripcion: p.descripcion || '',
      categoria: p.categoria || '',
      precio: String(p.precio ?? ''),
      pesoUnitarioKg: p.pesoUnitarioKg != null ? String(p.pesoUnitarioKg) : '',
      stockMinimo: String(p.stockMinimo ?? '0'),
      stockInicial: '0',
    })
    setErrores({})
    setErrorForm(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const actual = editando || form
  const setCampo = (campo) => (e) => {
    const valor = e.target.value
    if (editando) setEditando({ ...editando, [campo]: valor })
    else setForm({ ...form, [campo]: valor })
  }

  return (
    <section>
      <h1>Productos</h1>
      <p className="texto-suave">Alta, edición, activación y seguimiento del stock mínimo de los productos del catálogo.</p>

      <ErrorBox mensaje={error} onReintentar={cargar} />

      <div className="tarjeta formulario-producto">
        <h2>{editando ? `Editar producto #${editando.id}` : 'Nuevo producto'}</h2>
        <form onSubmit={enviar} noValidate>
          <div className="campo-trio">
            <label className="campo">
              <span>Código *</span>
              <input className="input" value={actual.codigo} onChange={setCampo('codigo')} placeholder="MED-001" />
              {errores.codigo && <small className="error-campo">{errores.codigo}</small>}
            </label>
            <label className="campo">
              <span>Nombre *</span>
              <input className="input" value={actual.nombre} onChange={setCampo('nombre')} placeholder="Amoxicilina 500 mg" />
              {errores.nombre && <small className="error-campo">{errores.nombre}</small>}
            </label>
            <label className="campo">
              <span>Categoría</span>
              <input className="input" value={actual.categoria} onChange={setCampo('categoria')} placeholder="MEDICAMENTO" />
            </label>
          </div>
          <label className="campo">
            <span>Descripción</span>
            <input className="input" value={actual.descripcion} onChange={setCampo('descripcion')} placeholder="Detalles del formato, envase…" />
          </label>
          <div className="campo-trio">
            <label className="campo">
              <span>Precio (€) *</span>
              <input className="input" type="number" min="0.01" step="0.01" value={actual.precio} onChange={setCampo('precio')} />
              {errores.precio && <small className="error-campo">{errores.precio}</small>}
            </label>
            <label className="campo">
              <span>Peso por unidad (kg)</span>
              <input className="input" inputMode="decimal" value={actual.pesoUnitarioKg} onChange={setCampo('pesoUnitarioKg')} placeholder="Sin peso" />
              <small className="texto-suave">Con embalaje. Vacío = desconocido: el pedido no se podrá preparar.</small>
              {errores.pesoUnitarioKg && <small className="error-campo">{errores.pesoUnitarioKg}</small>}
            </label>
            {!editando && (
              <label className="campo">
                <span>Stock inicial</span>
                <input className="input" type="number" min="0" step="1" value={actual.stockInicial} onChange={setCampo('stockInicial')} />
                {errores.stockInicial && <small className="error-campo">{errores.stockInicial}</small>}
              </label>
            )}
            <label className="campo">
              <span>Stock mínimo (aviso)</span>
              <input className="input" type="number" min="0" step="1" value={actual.stockMinimo} onChange={setCampo('stockMinimo')} />
              {errores.stockMinimo && <small className="error-campo">{errores.stockMinimo}</small>}
            </label>
          </div>
          <ErrorBox mensaje={errorForm} />
          <div className="acciones-fila">
            <button type="submit" className="btn btn-primario" disabled={guardando}>
              {guardando ? 'Guardando…' : editando ? 'Guardar cambios' : 'Crear producto'}
            </button>
            {editando && (
              <button type="button" className="btn btn-secundario" onClick={() => { setEditando(null); setForm(FORMULARIO_VACIO); setErrores({}) }}>
                Cancelar edición
              </button>
            )}
          </div>
        </form>
      </div>

      <Tabla
        columns={[
          { key: 'codigo', label: 'Código' },
          { key: 'nombre', label: 'Nombre' },
          { key: 'categoria', label: 'Categoría', soloEscritorio: true },
          { key: 'precio', label: 'Precio', render: (p) => dinero(p.precio) },
          { key: 'pesoUnitarioKg', label: 'Peso/ud.', render: (p) => (p.pesoUnitarioKg != null ? `${kg(p.pesoUnitarioKg)} kg` : <span className="etiqueta est-pago-pendiente">Sin peso</span>) },
          { key: 'stockDisponible', label: 'Stock disp.', render: (p) => `${p.stockDisponible ?? '—'}${p.stockReservado ? ` (${p.stockReservado} res.)` : ''}` },
          { key: 'aviso', label: 'Avisos', render: (p) => (<><BadgeStockBajo bajo={p.stockBajo ?? (p.stockDisponible <= p.stockMinimo)} /> {p.activo === false && <span className="etiqueta est-cancelado">⛔ Inactivo</span>}</>) },
          { key: 'activo', label: 'Estado', render: (p) => <BadgeActivo activo={p.activo} /> },
          {
            key: 'acciones',
            label: 'Acciones',
            render: (p) => (
              <div className="acciones-tabla">
                <button type="button" className="btn btn-mini btn-secundario" onClick={() => empezarEdicion(p)}>Editar</button>
                <button type="button" className={'btn btn-mini ' + (p.activo === false ? 'btn-primario' : 'btn-peligro')} onClick={() => alternarActivo(p)}>
                  {p.activo === false ? 'Activar' : 'Desactivar'}
                </button>
              </div>
            ),
          },
        ]}
        filas={productos}
        claveFila={(p) => p.id}
        busquedaCampos={['codigo', 'nombre', 'categoria', 'descripcion']}
        busquedaValor={busqueda}
        onBusqueda={setBusqueda}
        placeholderBusqueda="Buscar por código, nombre…"
        filtros={[
          {
            etiqueta: 'Solo stock bajo',
            valor: soloBajo ? 'si' : '',
            setValor: (v) => setSoloBajo(v === 'si'),
            opciones: [{ value: '', label: 'Todos los productos' }, { value: 'si', label: 'Solo stock bajo' }],
            cumple: (p) => true,
          },
        ]}
        textoVacio="No hay productos que coincidan."
        cargando={cargando}
      />
      {cargando && <Spinner texto="Cargando productos…" />}
    </section>
  )
}
