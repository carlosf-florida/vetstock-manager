import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

/**
 * Notificaciones breves («toasts») para toda la aplicación, sin librerías.
 *
 *   const { notificar } = useNotificaciones()
 *   notificar({ tipo: 'exito', texto: '...', enlace: { texto: 'Ver carrito', a: '/carrito' } })
 *
 * - Se ven como mucho MAXIMO a la vez: al llegar otra se quita la más antigua.
 * - Desaparecen solas (los errores duran algo más) y se pueden cerrar.
 * - La zona usa aria-live para que los lectores de pantalla las anuncien.
 */
const MAXIMO = 3
const DURACION_MS = { exito: 5000, error: 8000 }

const NotificacionesContext = createContext(null)

export function NotificacionesProvider({ children }) {
  const [lista, setLista] = useState([])
  const siguienteId = useRef(1)

  const cerrar = useCallback((id) => {
    setLista((actual) => actual.filter((n) => n.id !== id))
  }, [])

  const notificar = useCallback(({ tipo = 'exito', texto, enlace = null }) => {
    const id = siguienteId.current++
    setLista((actual) => [...actual, { id, tipo, texto, enlace }].slice(-MAXIMO))
    return id
  }, [])

  return (
    <NotificacionesContext.Provider value={{ notificar }}>
      {children}
      <div className="notificaciones" aria-live="polite" aria-relevant="additions">
        {lista.map((n) => <Notificacion key={n.id} datos={n} onCerrar={cerrar} />)}
      </div>
    </NotificacionesContext.Provider>
  )
}

function Notificacion({ datos, onCerrar }) {
  const { id, tipo, texto, enlace } = datos

  useEffect(() => {
    const temporizador = setTimeout(() => onCerrar(id), DURACION_MS[tipo] ?? DURACION_MS.exito)
    return () => clearTimeout(temporizador)
  }, [id, tipo, onCerrar])

  return (
    <div className={`notificacion notificacion-${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>
      <span className="notificacion-icono" aria-hidden="true">{tipo === 'error' ? '⚠️' : '✔️'}</span>
      <p className="notificacion-texto">{texto}</p>
      <div className="notificacion-acciones">
        {enlace && (
          <Link className="btn btn-mini btn-secundario" to={enlace.a} onClick={() => onCerrar(id)}>
            {enlace.texto}
          </Link>
        )}
        <button type="button" className="notificacion-cerrar" onClick={() => onCerrar(id)} aria-label="Cerrar aviso">
          ×
        </button>
      </div>
    </div>
  )
}

export function useNotificaciones() {
  return useContext(NotificacionesContext)
}
