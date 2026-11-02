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
| `/login` | Portada pública y acceso (sin datos privados) |
| `/inicio` | Inicio tras el login: pedido en curso, pendientes de firma y de pago, últimos pedidos y productos que más pide la clínica |
| `/catalogo` | Catálogo con búsqueda, categoría, precio máximo y aviso de stock bajo |
| `/carrito` | Carrito por usuario (guardado en `localStorage`), limitado al stock, y creación del pedido; administración elige clínica y veterinario |
| `/mis-pedidos` | Pedidos de la clínica con filtros por estado, firma y pago |
| `/pedidos/:id` | Detalle: líneas, cronología, firma (pestaña «Firmar» del veterinario asignado), pago y documentos |
| `/pago-simulado/:referencia` | Pasarela de pago de demostración (pago correcto, fallido y reintento) |

Si un usuario sin rol de administración abre una ruta `/admin/...`, se muestra la página «Acceso no permitido».

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
├── carrito.js        Carrito por usuario en localStorage (stock y cantidades)
├── format.js         Formateo de fechas y euros
├── components/       Layout (cabecera y contador), Notificaciones, Tabla, badges, gráficos, spinner
└── pages/            Páginas (pages/admin/ para la zona de almacén)
```

**Principio**: la vista nunca decide reglas de negocio. Si el backend devuelve 403, se muestra el error; los permisos reales se comprueban siempre en el servidor.
