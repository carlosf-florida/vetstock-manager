# Frontend (React + Vite)

Interfaz web de VetStock Manager. React 18 con React Router; la sesión va en la cookie `JSESSIONID` y todas las llamadas incluyen `credentials: "include"`.

## Arrancar

```bash
npm install
npm run dev        # http://localhost:5173 (proxy /api -> localhost:8080)
npm run build      # build de producción en dist/
```

En desarrollo, Vite hace de **proxy**: el navegador llama a `/api/...` en el 5173 y Vite lo reenvía al backend del 8080 (sin CORS).

## Páginas

### Portal del cliente (veterinarios y auxiliares)
| Ruta | Qué hace |
|---|---|
| `/login` | Inicio de sesión |
| `/catalogo` | Catálogo con búsqueda por texto, filtros de categoría y aviso de stock bajo |
| `/carrito` | Carrito (persistido en `localStorage`) y envío a firma con veterinario asignado |
| `/pedidos` | Mis pedidos con filtros por estado |
| `/pedidos/:id` | Detalle: cronología completa, líneas, firma, pago y documentos |
| `/pedidos/:id/firmar` | Firma con dibujo táctil/ratón o subiendo una imagen |
| `/pago-simulado/:referencia` | Pantalla del "banco" simulado (confirmar / fallar / reintento) |
| `/acceso-no-permitido` | Error 403 amigable |

### Administración (almacén)
| Ruta | Qué hace |
|---|---|
| `/admin` | Dashboard con contadores reales y últimos pedidos |
| `/admin/productos` | CRUD de productos (crear, editar, activar/desactivar) |
| `/admin/stock` | Entradas y salidas manuales + historial de movimientos |
| `/admin/clinicas` | Clínicas, veterinarios y auxiliares |
| `/admin/pedidos` | Todos los pedidos con filtros; acciones de preparación y expedición |
| `/admin/pedidos/:id` | Detalle logístico: preparar, expedir, bultos, documentos, firma |
| `/admin/bultos` · `/admin/palets` · `/admin/destinos` | Logística de expedición |
| `/admin/informes` | Productos top, evolución mensual, stock bajo, tiempos y destinos (gráficos CSS y tablas) |

## Estructura del código

```
src/
├── api.js            Cliente HTTP central (fetch con credenciales)
├── auth.jsx          Contexto de sesión y guards de rutas
├── carrito.js        Carrito en localStorage
├── format.js         Formateo de fechas y euros
├── components/       Tabla, badges, gráficos, layout, spinner
└── pages/            Páginas (pages/admin/ para la zona de almacén)
```

**Principio**: la vista nunca decide reglas de negocio. Si el backend devuelve 403, se muestra el error; los permisos reales se comprueban siempre en el servidor.
