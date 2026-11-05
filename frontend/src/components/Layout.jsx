import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { ROLES } from '../format.js'
import { EVENTO_CARRITO, unidadesCarrito } from '../carrito.js'

function Item({ to, children, onClick }) {
  return (
    // end en /admin: «Resumen» solo se marca en su propia ruta y no en /admin/pedidos, etc.
    <NavLink to={to} end={to === '/admin'} className={({ isActive }) => 'menu-enlace' + (isActive ? ' activo' : '')} onClick={onClick}>
      {children}
    </NavLink>
  )
}

// Acceso al carrito con el número de UNIDADES totales (no de productos distintos)
function AccesoCarrito({ onClick }) {
  const [unidades, setUnidades] = useState(() => unidadesCarrito())

  useEffect(() => {
    const refrescar = () => setUnidades(unidadesCarrito())
    refrescar()
    window.addEventListener(EVENTO_CARRITO, refrescar)
    return () => window.removeEventListener(EVENTO_CARRITO, refrescar)
  }, [])

  const etiqueta = unidades === 0 ? 'Carrito vacío' : `Carrito: ${unidades} ${unidades === 1 ? 'unidad' : 'unidades'}`
  return (
    <NavLink to="/carrito" className={({ isActive }) => 'carrito-acceso' + (isActive ? ' activo' : '')} onClick={onClick} aria-label={etiqueta} title={etiqueta}>
      <span aria-hidden="true">🛒</span>
      <span className="carrito-acceso-texto">Carrito</span>
      <span className="carrito-contador" aria-hidden="true">{unidades}</span>
    </NavLink>
  )
}

export default function Layout() {
  const { usuario, logout } = useAuth()
  const navigate = useNavigate()
  const [abierto, setAbierto] = useState(false)

  function cerrar() { setAbierto(false) }

  async function salir() {
    await logout()
    cerrar()
    navigate('/login')
  }

  const esAdmin = usuario?.rol === 'ADMIN'

  return (
    <div className="layout">
      <header className={'cabecera' + (esAdmin ? ' cabecera-admin' : '')}>
        <div className="cabecera-interior">
          <NavLink to="/" className="marca" onClick={cerrar}>
            <span aria-hidden="true">🐾</span> VetStock <span className="marca-suave">Manager</span>
          </NavLink>

          <nav className={'menu' + (abierto ? ' abierto' : '')} aria-label="Navegación principal">
            {usuario && (
              <>
                <span className="menu-seccion">Portal del cliente</span>
                {!esAdmin && <Item to="/inicio" onClick={cerrar}>Inicio</Item>}
                <Item to="/catalogo" onClick={cerrar}>Catálogo</Item>
                <Item to="/mis-pedidos" onClick={cerrar}>Mis pedidos</Item>
                {esAdmin && (
                  <>
                    <span className="menu-seccion">Administración</span>
                    <Item to="/admin" onClick={cerrar}>Resumen</Item>
                    <Item to="/admin/productos" onClick={cerrar}>Productos</Item>
                    <Item to="/admin/stock" onClick={cerrar}>Stock</Item>
                    <Item to="/admin/clinicas" onClick={cerrar}>Clínicas</Item>
                    <Item to="/admin/pedidos" onClick={cerrar}>Pedidos</Item>
                    <Item to="/admin/bultos" onClick={cerrar}>Bultos</Item>
                    <Item to="/admin/palets" onClick={cerrar}>Palets</Item>
                    <Item to="/admin/destinos" onClick={cerrar}>Salidas</Item>
                    <Item to="/admin/informes" onClick={cerrar}>Informes</Item>
                    <Item to="/admin/odoo" onClick={cerrar}>Odoo</Item>
                  </>
                )}
              </>
            )}
          </nav>

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
                <AccesoCarrito onClick={cerrar} />
                <button type="button" className="btn btn-secundario" onClick={salir}>Salir</button>
              </>
            )}
            <button
              type="button"
              className="menu-boton"
              aria-label="Abrir o cerrar el menú"
              aria-expanded={abierto}
              onClick={() => setAbierto((v) => !v)}
            >
              ☰
            </button>
          </div>
        </div>
      </header>

      <main className="contenido">
        <Outlet />
      </main>

      <footer className="pie">
        VetStock Manager · Proyecto académico de 2º DAM · Pasarela de pago simulada, sin cobros reales
      </footer>
    </div>
  )
}
