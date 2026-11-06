import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../../api.js'
import '../../publico.css'

const MINIMO = 8
const MAXIMO = 72

/** Página a la que lleva el enlace de un solo uso: aquí la persona elige su contraseña. */
export default function ActivarCuenta() {
  const { token } = useParams()
  const [contrasena, setContrasena] = useState('')
  const [repetida, setRepetida] = useState('')
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [usuario, setUsuario] = useState(null)

  async function enviar(e) {
    e.preventDefault()
    if (enviando) return
    if (contrasena.length < MINIMO || contrasena.length > MAXIMO) {
      setError(`La contraseña debe tener entre ${MINIMO} y ${MAXIMO} caracteres.`)
      return
    }
    if (contrasena !== repetida) {
      setError('Las contraseñas no coinciden.')
      return
    }
    setError(null)
    setEnviando(true)
    try {
      const r = await api.post('/api/publico/activacion', { token, contrasena })
      setUsuario(r.usuario)
    } catch (err) {
      setError(err.message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="publico">
      <header className="pub-cabecera">
        <div className="pub-ancho pub-cabecera-interior">
          <Link to="/" className="pub-marca"><span aria-hidden="true">🐾</span> VetStock Manager</Link>
        </div>
      </header>
      <main className="pub-seccion">
        <div className="pub-ancho pub-estrecho">
          <h1 className="pub-titulo">Activar el acceso</h1>
          {usuario ? (
            <div className="caja-ok" role="status">
              <p>Cuenta activada. Tu nombre de usuario es <strong>{usuario}</strong>.</p>
              <Link className="btn btn-primario" to="/login">Iniciar sesión</Link>
            </div>
          ) : (
            <form onSubmit={enviar} noValidate>
              <p className="texto-suave">Elige tu contraseña. Este enlace solo se puede usar una vez.</p>
              <label className="campo" htmlFor="activar-contrasena">
                <span>Contraseña nueva</span>
                <input id="activar-contrasena" className="input" type="password" autoComplete="new-password" maxLength={MAXIMO}
                  value={contrasena} onChange={(e) => setContrasena(e.target.value)} aria-describedby="activar-ayuda" />
                <small id="activar-ayuda" className="texto-suave">Entre {MINIMO} y {MAXIMO} caracteres.</small>
              </label>
              <label className="campo" htmlFor="activar-repetida">
                <span>Repite la contraseña</span>
                <input id="activar-repetida" className="input" type="password" autoComplete="new-password" maxLength={MAXIMO}
                  value={repetida} onChange={(e) => setRepetida(e.target.value)} />
              </label>
              {error && <p className="caja-error" role="alert">{error}</p>}
              <button className="btn btn-primario btn-grande" type="submit" disabled={enviando}>
                {enviando ? 'Activando…' : 'Activar cuenta'}
              </button>
            </form>
          )}
        </div>
      </main>
    </div>
  )
}
