import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { ROLES } from '../format.js'
import { EVENTO_CARRITO, unidadesCarrito } from '../carrito.js'
import MenuLateral, { GrupoMenu, usePantallaPequena } from './MenuLateral.jsx'

// Preferencia de interfaz de este navegador (abierto o cerrado en escritorio); no guarda datos personales
const CLAVE_MENU = 'vetstock.menuLateral'

const PORTAL = [
  { to: '/inicio', texto: 'Inicio', soloClientes: true },
  { to: '/catalogo', texto: 'Catálogo' },
  { to: '/mis-pedidos', texto: 'Mis pedidos' },
]
const ADMINISTRACION = [
  { to: '/admin', texto: 'Resumen' },
  { to: '/admin/productos', texto: 'Productos' },
  { to: '/admin/stock', texto: 'Stock' },
  { to: '/admin/clinicas', texto: 'Clínicas' },
  { to: '/admin/solicitudes', texto: 'Solicitudes de alta' },
  { to: '/admin/pedidos', texto: 'Pedidos' },
  { to: '/admin/bultos', texto: 'Bultos' },
  { to: '/admin/palets', texto: 'Palets' },
  { to: '/admin/destinos', texto: 'Salidas' },
  { to: '/admin/informes', texto: 'Informes' },
  { to: '/admin/odoo', texto: 'Odoo' },
]

function leerPreferencia() {
  try {
    return localStorage.getItem(CLAVE_MENU) !== 'cerrado'
  } catch {
    return true
  }
}

function Enlaces({ lista }) {
  // end en /admin: «Resumen» solo se marca en su propia ruta y no en /admin/pedidos, etc.
  return lista.map((e) => (
    <li key={e.to}>
      <NavLink to={e.to} end={e.to === '/admin'} className={({ isActive }) => 'lateral-enlace' + (isActive ? ' activo' : '')}>
        {e.texto}
      </NavLink>
    </li>
  ))
}

// Acceso al carrito con el número de UNIDADES totales (no de productos distintos)
function AccesoCarrito() {
  const [unidades, setUnidades] = useState(() => unidadesCarrito())

  useEffect(() => {
    const refrescar = () => setUnidades(unidadesCarrito())
    refrescar()
    window.addEventListener(EVENTO_CARRITO, refrescar)
    return () => window.removeEventListener(EVENTO_CARRITO, refrescar)
  }, [])

  const etiqueta = unidades === 0 ? 'Carrito vacío' : `Carrito: ${unidades} ${unidades === 1 ? 'unidad' : 'unidades'}`
  return (
    <NavLink to="/carrito" className={({ isActive }) => 'carrito-acceso' + (isActive ? ' activo' : '')} aria-label={etiqueta} title={etiqueta}>
      <span aria-hidden="true">🛒</span>
      <span className="carrito-acceso-texto">Carrito</span>
      <span className="carrito-contador" aria-hidden="true">{unidades}</span>
    </NavLink>
  )
}

export default function Layout() {
  const { usuario, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const pequena = usePantallaPequena()
  const boton = useRef(null)
  const [abiertoEscritorio, setAbiertoEscritorio] = useState(leerPreferencia)
  const [abiertoMovil, setAbiertoMovil] = useState(false)
  const abierto = pequena ? abiertoMovil : abiertoEscritorio
  const superpuesto = pequena

  // En pantalla pequeña el panel se cierra al elegir una ruta
  useEffect(() => { setAbiertoMovil(false) }, [pathname])

  function alternar() {
    if (pequena) {
      setAbiertoMovil((v) => !v)
      return
    }
    setAbiertoEscritorio((v) => {
      try { localStorage.setItem(CLAVE_MENU, v ? 'cerrado' : 'abierto') } catch { /* sin preferencia guardada */ }
      return !v
    })
  }

  async function salir() {
    await logout()
    navigate('/login')
  }

  const esAdmin = usuario?.rol === 'ADMIN'
  const portal = PORTAL.filter((e) => !(esAdmin && e.soloClientes))

  return (
    <div className="layout">
      <header className="cabecera">
        <div className="cabecera-interior">
          {usuario && (
            <button ref={boton} type="button" className="menu-boton" aria-expanded={abierto} aria-controls="menu-lateral" onClick={alternar}>
              <span aria-hidden="true">☰</span> <span className="menu-boton-texto">Menú</span>
            </button>
          )}
          <NavLink to="/portal" className="marca">
            <span aria-hidden="true">🐾</span> VetStock <span className="marca-suave">Manager</span>
          </NavLink>

          <div className="cabecera-derecha">
            {usuario && (
              <>
                <span className="usuario-info">
                  <strong>{usuario.nombreCompleto || usuario.username}</strong>
                  <small>
                    {ROLES[usuario.rol] || usuario.rol}
                    {usuario.clinicaNombre ? ` · ${usuario.clinicaNombre}` : ''}
                  </small>
                </span>
                <AccesoCarrito />
                <button type="button" className="btn btn-secundario" onClick={salir}>Salir</button>
              </>
            )}
          </div>
        </div>
      </header>

      <div className="marco">
        {usuario && (
          <MenuLateral id="menu-lateral" etiqueta="Navegación principal" abierto={abierto} superpuesto={superpuesto}
            onCerrar={() => setAbiertoMovil(false)} botonRef={boton}>
            {esAdmin ? (
              <>
                <GrupoMenu id="grupo-portal" titulo="Portal del cliente" contieneActiva={portal.some((e) => pathname === e.to)}>
                  <Enlaces lista={portal} />
                </GrupoMenu>
                <GrupoMenu id="grupo-admin" titulo="Administración" contieneActiva={pathname.startsWith('/admin')}>
                  <Enlaces lista={ADMINISTRACION} />
                </GrupoMenu>
              </>
            ) : (
              <>
                <p className="lateral-titulo">Portal del cliente</p>
                <ul className="lateral-lista"><Enlaces lista={portal} /></ul>
              </>
            )}
          </MenuLateral>
        )}

        {/* Con el panel superpuesto abierto, lo de detrás no recibe foco ni clics */}
        <div className="marco-contenido" inert={abierto && superpuesto ? '' : undefined}>
          <main className="contenido">
            <Outlet />
          </main>
          <footer className="pie">
            VetStock Manager · Proyecto académico de 2º DAM · Pasarela de pago simulada, sin cobros reales
          </footer>
        </div>
      </div>
    </div>
  )
}
