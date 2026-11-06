// Datos del apartado «Sobre el desarrollador» de la web pública.
//
// Fotografía: copia la imagen en frontend/public/ (por ejemplo, desarrollador.jpg) y escribe
// aquí su ruta: foto: '/desarrollador.jpg'. Si queda vacía, se muestra un marcador neutro.
//
// Enlaces: rellena solo los que quieras publicar. Un enlace vacío o no válido no se muestra.
export const DESARROLLADOR = {
  nombre: 'Carlos Fernandez Escarti',
  descripcion:
    'Estudiante de segundo de Desarrollo de Aplicaciones Multiplataforma y desarrollador de VetStock Manager como proyecto individual.',
  foto: '',
  email: '',
  github: '',
  linkedin: '',
}

// Tecnologías comprobadas en el código del proyecto
export const TECNOLOGIAS = [
  'Java 21', 'Spring Boot 4', 'Maven', 'MySQL 8', 'React 18', 'Vite 5', 'JUnit 5', 'Docker', 'Odoo 18 Community',
]

export function urlValida(url) {
  try {
    return ['http:', 'https:'].includes(new URL(url).protocol)
  } catch {
    return false
  }
}

export function emailValido(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email || '')
}
