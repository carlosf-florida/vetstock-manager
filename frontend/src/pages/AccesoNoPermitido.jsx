import { Link } from 'react-router-dom'
import { useAuth } from '../auth.jsx'

export default function AccesoNoPermitido() {
  const { usuario } = useAuth()
  return (
    <div className="centrado-pagina">
      <div className="tarjeta tarjeta-aviso">
        <h1><span aria-hidden="true">⛔</span> Acceso no permitido</h1>
        <p>
          Tu cuenta ({usuario?.rol === 'ADMIN' ? 'Administración' : usuario?.rol || 'sin rol'}) no tiene permisos
          para ver esta sección. Si crees que es un error, contacta con la administración de la distribuidora.
        </p>
        <Link className="btn btn-primario" to={usuario?.rol === 'ADMIN' ? '/admin' : '/catalogo'}>
          Volver a mi área
        </Link>
      </div>
    </div>
  )
}
