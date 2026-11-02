import { createContext, useContext, useEffect, useState } from 'react'
import { apiFetch } from './api.js'
import { usarCarritoDe } from './carrito.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null)
  const [cargando, setCargando] = useState(true)

  // Cada cuenta usa su propio carrito: al entrar o salir se cambia de carrito
  useEffect(() => { usarCarritoDe(usuario?.username) }, [usuario?.username])

  useEffect(() => {
    let activo = true
    apiFetch('/api/auth/me')
      .then((datos) => { if (activo) setUsuario(datos) })
      .catch(() => { if (activo) setUsuario(null) })
      .finally(() => { if (activo) setCargando(false) })
    return () => { activo = false }
  }, [])

  async function login(username, password) {
    // Login de formulario de Spring Security: application/x-www-form-urlencoded
    const datos = await apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ username, password }).toString(),
    })
    // Refrescamos el perfil con /me por coherencia
    try {
      const yo = await apiFetch('/api/auth/me')
      setUsuario(yo || datos)
    } catch {
      setUsuario(datos)
    }
    return datos
  }

  async function logout() {
    try { await apiFetch('/api/auth/logout', { method: 'POST' }) } catch { /* ignorar */ }
    setUsuario(null)
  }

  return (
    <AuthContext.Provider value={{ usuario, cargando, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
