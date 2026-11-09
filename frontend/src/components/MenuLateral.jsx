import { useEffect, useRef, useState } from 'react'

const CONSULTA_PEQUENA = '(max-width: 860px)'

/** true en pantallas estrechas, donde el menú se abre como panel superpuesto. */
export function usePantallaPequena() {
  const [pequena, setPequena] = useState(() => window.matchMedia(CONSULTA_PEQUENA).matches)
  useEffect(() => {
    const consulta = window.matchMedia(CONSULTA_PEQUENA)
    const cambiar = () => setPequena(consulta.matches)
    consulta.addEventListener('change', cambiar)
    return () => consulta.removeEventListener('change', cambiar)
  }, [])
  return pequena
}

/** Grupo de enlaces que se puede plegar. Si contiene la pantalla activa, se abre solo. */
export function GrupoMenu({ id, titulo, contieneActiva, children }) {
  const [abierto, setAbierto] = useState(true)
  useEffect(() => {
    if (contieneActiva) setAbierto(true)
  }, [contieneActiva])
  return (
    <div className="lateral-grupo">
      <button type="button" className="lateral-grupo-titulo" aria-expanded={abierto} aria-controls={id}
        onClick={() => setAbierto((v) => !v)}>
        <span>{titulo}</span>
        <span aria-hidden="true">{abierto ? '▾' : '▸'}</span>
      </button>
      <ul id={id} className="lateral-lista" hidden={!abierto}>{children}</ul>
    </div>
  )
}

/**
 * Menú lateral izquierdo. En escritorio ocupa una columna y el contenido se aparta; en modo
 * superpuesto (pantallas pequeñas) se abre encima con un fondo oscuro, se cierra con Escape o
 * pulsando fuera, y devuelve el foco al botón que lo abrió.
 * Mostrar un enlace no da permisos: el backend comprueba cada petición.
 */
export default function MenuLateral({ id, etiqueta, abierto, superpuesto, onCerrar, botonRef, children }) {
  const panel = useRef(null)

  function cerrarYVolver() {
    onCerrar()
    botonRef?.current?.focus()
  }

  useEffect(() => {
    if (!abierto || !superpuesto) return undefined
    panel.current?.querySelector('a, button')?.focus()
    const tecla = (e) => {
      if (e.key === 'Escape') cerrarYVolver()
    }
    document.addEventListener('keydown', tecla)
    // La página de detrás no se desplaza mientras el panel está abierto
    const desbordamiento = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', tecla)
      document.body.style.overflow = desbordamiento
    }
  }, [abierto, superpuesto])

  return (
    <>
      {abierto && superpuesto && <div className="lateral-fondo" onClick={cerrarYVolver} aria-hidden="true" />}
      <nav id={id} ref={panel} aria-label={etiqueta} hidden={!abierto}
        className={'lateral' + (superpuesto ? ' lateral-superpuesto' : '')}>
        {superpuesto && (
          <button type="button" className="lateral-cerrar" onClick={cerrarYVolver}>
            <span aria-hidden="true">✕</span> Cerrar menú
          </button>
        )}
        {children}
      </nav>
    </>
  )
}
