import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import { Spinner } from './components/Spinner.jsx'
import Layout from './components/Layout.jsx'
import Login from './pages/Login.jsx'
import AccesoNoPermitido from './pages/AccesoNoPermitido.jsx'
import Inicio from './pages/Inicio.jsx'
import Catalogo from './pages/Catalogo.jsx'
import Carrito from './pages/Carrito.jsx'
import MisPedidos from './pages/MisPedidos.jsx'
import PedidoDetalle from './pages/PedidoDetalle.jsx'
import PasarelaPago from './pages/PasarelaPago.jsx'
import AdminDashboard from './pages/admin/Dashboard.jsx'
import AdminProductos from './pages/admin/Productos.jsx'
import AdminStock from './pages/admin/Stock.jsx'
import AdminClinicas from './pages/admin/Clinicas.jsx'
import AdminPedidos from './pages/admin/Pedidos.jsx'
import AdminPedidoDetalle from './pages/admin/PedidoDetalleAdmin.jsx'
import AdminBultos from './pages/admin/Bultos.jsx'
import AdminPalets from './pages/admin/Palets.jsx'
import AdminDestinos from './pages/admin/Destinos.jsx'
import AdminInformes from './pages/admin/Informes.jsx'
import AdminOdoo from './pages/admin/Odoo.jsx'

function RedirigirSegunRol() {
  const { usuario } = useAuth()
  return <Navigate to={usuario?.rol === 'ADMIN' ? '/admin' : '/inicio'} replace />
}

// Guard: exige sesión iniciada
function RequiereSesion({ children }) {
  const { usuario, cargando } = useAuth()
  const location = useLocation()
  if (cargando) return <div className="centrado-pagina"><Spinner /> <p>Comprobando sesión…</p></div>
  if (!usuario) return <Navigate to="/login" state={{ desde: location.pathname }} replace />
  return children
}

// Guard: exige rol ADMIN; en caso contrario muestra página de acceso no permitido
function RequiereAdmin({ children }) {
  const { usuario, cargando } = useAuth()
  if (cargando) return <div className="centrado-pagina"><Spinner /> <p>Comprobando sesión…</p></div>
  if (!usuario) return <Navigate to="/login" replace />
  if (usuario.rol !== 'ADMIN') return <AccesoNoPermitido />
  return children
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/pago-simulado/:referencia" element={<PasarelaPago />} />
      <Route element={<Layout />}>
        <Route path="/" element={<RedirigirSegunRol />} />

        {/* Portal del cliente (también accesible a ADMIN) */}
        <Route path="/inicio" element={<RequiereSesion><Inicio /></RequiereSesion>} />
        <Route path="/catalogo" element={<RequiereSesion><Catalogo /></RequiereSesion>} />
        <Route path="/carrito" element={<RequiereSesion><Carrito /></RequiereSesion>} />
        <Route path="/mis-pedidos" element={<RequiereSesion><MisPedidos /></RequiereSesion>} />
        <Route path="/pedidos/:id" element={<RequiereSesion><PedidoDetalle /></RequiereSesion>} />

        {/* Panel de administración (solo ADMIN) */}
        <Route path="/admin" element={<RequiereAdmin><AdminDashboard /></RequiereAdmin>} />
        <Route path="/admin/productos" element={<RequiereAdmin><AdminProductos /></RequiereAdmin>} />
        <Route path="/admin/stock" element={<RequiereAdmin><AdminStock /></RequiereAdmin>} />
        <Route path="/admin/clinicas" element={<RequiereAdmin><AdminClinicas /></RequiereAdmin>} />
        <Route path="/admin/pedidos" element={<RequiereAdmin><AdminPedidos /></RequiereAdmin>} />
        <Route path="/admin/pedidos/:id" element={<RequiereAdmin><AdminPedidoDetalle /></RequiereAdmin>} />
        <Route path="/admin/bultos" element={<RequiereAdmin><AdminBultos /></RequiereAdmin>} />
        <Route path="/admin/palets" element={<RequiereAdmin><AdminPalets /></RequiereAdmin>} />
        <Route path="/admin/destinos" element={<RequiereAdmin><AdminDestinos /></RequiereAdmin>} />
        <Route path="/admin/informes" element={<RequiereAdmin><AdminInformes /></RequiereAdmin>} />
        <Route path="/admin/odoo" element={<RequiereAdmin><AdminOdoo /></RequiereAdmin>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
