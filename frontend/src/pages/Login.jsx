import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { Spinner } from '../components/Spinner.jsx'

export default function Login() {
  const { usuario, cargando, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState(null)
  const [entrando, setEntrando] = useState(false)

  if (!cargando && usuario) {
    return <Navigate to={location.state?.desde || '/'} replace />
  }

  async function enviar(e) {
    e.preventDefault()
    const errs = {}
    if (!username.trim()) errs.username = 'Escribe tu usuario.'
    if (!password) errs.password = 'Escribe tu contraseña.'
    setErrores(errs)
    if (Object.keys(errs).length > 0) return
    setError(null)
    setEntrando(true)
    try {
      await login(username.trim(), password)
      // '/' decide según el rol: administración → /admin, clínicas → /inicio
      navigate(location.state?.desde || '/', { replace: true })
    } catch (err) {
      setError(err.message || 'No se ha podido iniciar sesión.')
    } finally {
      setEntrando(false)
    }
  }

  return (
    <div className="login-fondo">
      <div className="login-contenedor">
      <section className="login-presentacion" aria-labelledby="titulo-presentacion">
        <p className="login-marca"><span aria-hidden="true">🐾</span> VetStock Manager</p>
        <h2 id="titulo-presentacion">Pedidos de productos veterinarios para tu clínica</h2>
        <ul className="login-pasos">
          <li><strong>Pide</strong> desde el catálogo, con el stock disponible a la vista.</li>
          <li><strong>Autoriza:</strong> el veterinario asignado revisa y firma el pedido.</li>
          <li><strong>Paga</strong> el pedido firmado; la confirmación es automática.</li>
          <li><strong>Sigue</strong> la preparación y el envío desde «Mis pedidos».</li>
        </ul>
        <p className="login-aviso">Proyecto académico: datos ficticios y pasarela de pago de demostración, sin cobros reales.</p>
      </section>
      <form className="login-tarjeta" onSubmit={enviar} noValidate>
        <h1 className="login-titulo"><span aria-hidden="true">🐾</span> VetStock Manager</h1>
        <p className="login-subtitulo">Distribuidora de productos veterinarios · Acceso al portal</p>

        {error && (
          <div className="caja-error" role="alert">
            <span aria-hidden="true">⚠️</span>
            <div className="caja-error-texto">{error}</div>
          </div>
        )}

        <label className="campo">
          <span>Usuario</span>
          <input
            className="input"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            placeholder="p. ej. admin"
          />
          {errores.username && <small className="error-campo">{errores.username}</small>}
        </label>

        <label className="campo">
          <span>Contraseña</span>
          <input
            className="input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            placeholder="Tu contraseña"
          />
          {errores.password && <small className="error-campo">{errores.password}</small>}
        </label>

        <button type="submit" className="btn btn-primario btn-grande" disabled={entrando}>
          {entrando ? 'Entrando…' : 'Iniciar sesión'}
        </button>

        {entrando && <Spinner texto="Verificando credenciales…" />}

        <p className="login-nota">
          Si no tienes cuenta, pide acceso a la administración de la distribuidora.
        </p>
      </form>
      </div>
    </div>
  )
}
