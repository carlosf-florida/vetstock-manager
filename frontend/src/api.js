// Cliente HTTP central: todas las peticiones van con credenciales (cookie de sesión)
// y contra rutas relativas /api (mismo origen gracias al proxy de Vite).

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

function mensajeError(status, texto) {
  const porEstado = {
    400: 'Datos no válidos o regla de negocio incumplida.',
    401: 'No has iniciado sesión o la sesión ha caducado.',
    403: 'No tienes permisos para realizar esta operación.',
    404: 'El recurso solicitado no existe.',
  }
  return texto || porEstado[status] || `Error ${status} al contactar con el servidor.`
}

// Aviso al resto de la aplicación (auth.jsx) de que la sesión ya no es válida
export const EVENTO_SESION_CADUCADA = 'vetstock:sesion-caducada'

export async function apiFetch(url, options = {}) {
  const opts = { credentials: 'include', ...options }
  if (opts.body && !(opts.body instanceof FormData) && typeof opts.body !== 'string') {
    opts.headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) }
    opts.body = JSON.stringify(opts.body)
  }
  let res
  try {
    res = await fetch(url, opts)
  } catch {
    throw new ApiError(0, 'No se ha podido conectar con el servidor. Comprueba tu conexión e inténtalo de nuevo.')
  }
  if (res.status === 204) return null
  const tipo = res.headers.get('content-type') || ''
  if (!res.ok) {
    // /api/auth/* queda fuera: ahí un 401 significa «contraseña incorrecta» o «aún sin sesión»
    if (res.status === 401 && !url.startsWith('/api/auth/')) {
      window.dispatchEvent(new Event(EVENTO_SESION_CADUCADA))
    }
    let texto = null
    try {
      const cuerpo = await res.text()
      if (cuerpo) {
        try {
          const datos = JSON.parse(cuerpo)
          texto = datos.message || datos.error || datos.mensaje || (typeof datos === 'string' ? datos : null)
        } catch {
          texto = cuerpo
        }
      }
    } catch { /* ignorar */ }
    throw new ApiError(res.status, mensajeError(res.status, texto))
  }
  if (tipo.includes('application/json')) return res.json()
  return res
}

// Atajos semánticos
export const api = {
  get: (url) => apiFetch(url),
  post: (url, body) => apiFetch(url, { method: 'POST', body }),
  put: (url, body) => apiFetch(url, { method: 'PUT', body }),
  patch: (url, body) => apiFetch(url, { method: 'PATCH', body: body ?? undefined }),
  delete: (url) => apiFetch(url, { method: 'DELETE' }),
  // Subida multipart: el navegador pone el Content-Type con el boundary
  postForm: (url, formData) => apiFetch(url, { method: 'POST', body: formData }),
  // Descarga de binarios (documentos, imagen de firma)
  blob: async (url) => {
    const res = await apiFetch(url)
    return res.blob()
  },
}

export async function descargarArchivo(url, nombreSugerido) {
  const blob = await api.blob(url)
  const enlace = document.createElement('a')
  const objetoUrl = URL.createObjectURL(blob)
  enlace.href = objetoUrl
  enlace.download = nombreSugerido
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  URL.revokeObjectURL(objetoUrl)
}
