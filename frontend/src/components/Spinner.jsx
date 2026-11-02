export function Spinner({ texto = 'Cargando…' }) {
  return (
    <div className="spinner-zona" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true"></span>
      <span className="spinner-texto">{texto}</span>
    </div>
  )
}

export function ErrorBox({ mensaje, onReintentar }) {
  if (!mensaje) return null
  return (
    <div className="caja-error" role="alert">
      <span aria-hidden="true">⚠️</span>
      <div className="caja-error-texto">{mensaje}</div>
      {onReintentar && (
        <button type="button" className="btn btn-secundario" onClick={onReintentar}>
          Reintentar
        </button>
      )}
    </div>
  )
}

export function SinDatos({ mensaje = 'No hay datos para mostrar.' }) {
  return (
    <div className="caja-vacia">
      <span aria-hidden="true">🗂️</span>
      <p>{mensaje}</p>
    </div>
  )
}
