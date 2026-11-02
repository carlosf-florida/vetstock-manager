# Guía del proyecto VetStock Manager

Qué hace cada parte del proyecto, cómo se conectan y qué archivos tocar para cada cambio.

## Visión general

Aplicación de tres capas con frontend y backend separados:

```
┌─────────────────────┐         ┌─────────────────────────────┐        ┌──────────┐
│  Frontend React     │  HTTP   │  Backend Spring Boot        │  JDBC  │  MySQL   │
│  (Vite, puerto 5173)│ ──────► │  REST (puerto 8080)         │ ─────► │ vetstock │
│  Interfaz de usuario│  JSON   │  Lógica de negocio y acceso │  JPA/  │          │
│                     │         │  a datos                    │ Hibernate          │
└─────────────────────┘         └─────────────────────────────┘        └──────────┘
```

- El **frontend React** es solo la vista: llama a la API REST con `fetch`, guarda la sesión en la cookie `JSESSIONID` y no decide nada importante por su cuenta.
- El **backend Spring Boot** concentra TODA la lógica de negocio: cálculo de importes, reserva y descuento de stock, permisos, transiciones de estado y validación de pagos.
- **MySQL** guarda los datos. Las tablas se crean con un script SQL (`database/migrations/001_esquema_inicial.sql`); Hibernate solo lee y escribe, nunca modifica el esquema (`ddl-auto=none`).

En desarrollo, el servidor de Vite hace de **proxy**: el navegador llama a `http://localhost:5173/api/...` y Vite lo reenvía al backend del 8080, evitando CORS.

## Mapa del proyecto

| Carpeta | Responsabilidad |
|---|---|
| `frontend/` | Interfaz web (React + Vite). Solo muestra datos y llama a la API |
| `backend/` | API REST (Spring Boot). Todas las reglas de negocio y permisos |
| `database/migrations/` | Scripts MySQL: `001` crea todo con datos ficticios, `002`–`008` actualizan bases existentes |
| `docs/` | Documentación vigente (esta guía, reglas, instalación, pruebas…) |
| `scripts/` | `arrancar_odoo.sh` (integración ERP opcional) |
| `docker/`, `docker-compose.yml` | Arranque de todo con Docker |
| `historico/` | Prototipo v1, solo para consulta; no se usa |

## Estructura del frontend

```
frontend/src
├── main.jsx            Punto de entrada: router, sesión y notificaciones
├── App.jsx             Rutas y protección por sesión/rol
├── api.js              Llamadas HTTP a /api (con la cookie de sesión)
├── auth.jsx            Sesión: usuario actual, login y logout
├── carrito.js          Carrito por usuario en localStorage (añadir, cantidades, stock)
├── format.js           Formato de moneda y fechas, nombres de estados, estimación de IVA
├── pedidoEstado.js     Quién puede firmar o pagar (solo para mostrar botones)
├── styles.css          Estilos de toda la aplicación (colores en :root)
├── components/         Piezas reutilizables: Layout (cabecera), Notificaciones, Tabla, Badges…
│   └── pedido/         Partes del detalle de un pedido: líneas, firma, pago, documentos
└── pages/              Pantallas: Inicio, Catalogo, Carrito, MisPedidos, PedidoDetalle…
    └── admin/          Pantallas de administración
```

### Qué archivo tocar

| Quiero cambiar… | Archivos |
|---|---|
| Colores, tipografía, espaciados | `frontend/src/styles.css` (variables en `:root`) |
| Cabecera, menú y contador del carrito | `frontend/src/components/Layout.jsx` |
| Inicio del cliente | `frontend/src/pages/Inicio.jsx` |
| Portada (pantalla de acceso) | `frontend/src/pages/Login.jsx` |
| Catálogo y botón «Añadir al carrito» | `frontend/src/pages/Catalogo.jsx` |
| Reglas del carrito (stock, cantidades, por usuario) | `frontend/src/carrito.js` |
| Pantalla del carrito y creación del pedido | `frontend/src/pages/Carrito.jsx` |
| Avisos breves («se ha añadido…») | `frontend/src/components/Notificaciones.jsx` |
| Formato de euros | `frontend/src/format.js` (`dinero`) |
| Productos (datos) | `backend/.../service/ProductoService.java`, tabla `productos` |
| Reglas de pedidos (stock, firma, estados) | `backend/.../service/PedidoService.java`, `enums/EstadoPedido.java` |

## Recorrido de una acción: «Enviar a firma»

1. **Pantalla** `pages/Carrito.jsx`: el botón llama a `api.post('/api/pedidos', …)` y después a
   `api.patch('/api/pedidos/{id}/enviar-firma')` (`api.js` añade la cookie de sesión).
2. **Proxy**: en desarrollo, Vite reenvía `/api` al backend del puerto 8080.
3. **Seguridad** `config/SecurityConfig.java`: comprueba que hay sesión y el rol permitido.
4. **Controlador** `controller/PedidoController.java`: recibe la petición y valida el cuerpo (DTO).
5. **Servicio** `service/PedidoService.java`: comprueba que el pedido es de la clínica del usuario,
   que está en BORRADOR, y reserva el stock con una sola sentencia SQL por línea.
6. **Repositorio** `repository/ProductoRepository.java` → **MySQL** (tablas `productos` y `pedidos`).
7. La respuesta vuelve como `PedidoResponse` (JSON) y la pantalla navega al detalle del pedido.

El carrito NO pasa por el servidor hasta ese momento: vive en el navegador
(`carrito.js`), separado por usuario. El servidor recalcula precios y vuelve a
comprobar el stock al crear el pedido, así que los datos del navegador nunca deciden nada.

## Estructura del backend (arquitectura en capas)

```
es.florida.vet
├── config/        Configuración (seguridad con Spring Security)
├── controller/    Controladores REST: reciben peticiones HTTP y devuelven JSON
├── dto/           Objetos de transferencia: lo que entra y sale de la API
├── entity/        Entidades JPA: mapean las tablas de MySQL
├── enums/         Rol, estados del pedido, tipo de firma, etc.
├── exception/     Excepciones de negocio y manejador global de errores
├── repository/    Capa de acceso a datos (patrón DAO con Spring Data JPA)
├── service/       Lógica de negocio (los casos de uso del enunciado)
└── util/          Utilidades (BCrypt, fechas)
```

Petición típica: `Controller` (HTTP) → `Service` (reglas de negocio, transacciones) → `Repository` (SQL) → MySQL. Y de vuelta con DTOs, nunca exponiendo las entidades directamente.

## Patrones de diseño utilizados

| Patrón | Dónde | Para qué |
|---|---|---|
| **MVC** | `controller/` + `service/` + `repository/` | Separar la gestión HTTP de la lógica y del acceso a datos |
| **DAO (Repositorio)** | `repository/*Repository.java` | Acceso a datos sin SQL repartido por el código; Spring Data genera las consultas |
| **DTO** | `dto/*.java` | No exponer las entidades JPA; controlar exactamente qué datos viajan |
| **Adaptador (Adapter)** | `service/PagoService.java` + `application.properties` | Aislar el pago de un proveedor externo. Hoy se usa un proveedor **SIMULADO**; con Redsys u otro real bastaría cambiar el adaptador sin tocar pedidos ni estados |
| **Adaptador (Adapter) 2: ERP** | `odoo/OdooConnector.java` (+ `OdooSimuladoConnector` / `OdooJsonRpcConnector`) | Integración con el ERP **Odoo** elegida por configuración: simulado sin servidor o real por JSON-RPC |
| **Máquina de estados** | `enums/EstadoPedido.java` | Las transiciones válidas del pedido están definidas en una sola clase (`puedePasarA`); el resto del código la consulta y no puede "saltarse" estados |
| **Inyección de dependencias** | Todo el backend | Spring construye e inyecta los servicios y repositorios; facilita pruebas y sustitución de componentes |
| **Fachada** | `service/DashboardService`, `InformeService` | Reúnen datos de varios repositorios en una sola respuesta simple para la vista |
| **Proxy** | `vite.config.js` (desarrollo) | El servidor de Vite reenvía `/api` al backend |

### El adaptador de pagos (detallado)

El pago usa un adaptador de DEMOSTRACIÓN (no cobra). En este prototipo:

1. El backend crea el intento de pago con **referencia única** y **importe calculado por el servidor**.
2. La "pantalla del banco" está simulada en el propio frontend (`/pago-simulado/:referencia`).
3. El resultado llega por `POST /api/pagos/notificacion` (el webhook que en producción llamaría el proveedor), autenticado con una **firma HMAC-SHA256** del cuerpo (cabecera `X-Firma-Notificacion`) y procesado con bloqueo del pago.
4. La notificación **valida importe y moneda** y es **idempotente**: si llega dos veces, la segunda no tiene efecto.
5. Solo se puede iniciar el pago de un pedido FIRMADO. Una notificación `CONFIRMADO` lo pasa a `LISTO_PREPARAR` y solicita exportar a Odoo después del commit. El backend nunca se fía de la pantalla del navegador.

**No es una integración bancaria real**: no hay credenciales de Redsys ni tarjeta. Es el esqueleto correcto para conectar un proveedor de pago real más adelante, y el código lo documenta así.

### El adaptador de Odoo (detallado)

La interfaz `OdooConnector` define las tres operaciones que necesita la distribuidora:
`crearProducto`, `crearCliente` y `crearPedido`. Hay dos implementaciones y
Spring elige una según la propiedad `vetstock.odoo.modo`:

- **`OdooSimuladoConnector`** (modo `SIMULADO`, por defecto): mismo contrato y
  mismas reglas que el real, pero guarda los identificadores en memoria. Permite
  desarrollar y presentar el proyecto sin servidor ERP.
- **`OdooJsonRpcConnector`** (modo `REAL`): habla con un servidor **Odoo 18**
  real mediante su API JSON-RPC (`/jsonrpc`, servicio `object`, método
  `execute_kw`). Crea `product.template` (catálogo), `res.partner` (clínicas) y
  `sale.order` con sus `sale.order.line` (pedidos pagados, aunque estén por firmar).

`OdooSincronizacionService` orquesta el flujo y deja traza en la tabla
`odoo_log`; las tablas `productos`, `clinicas` y `pedidos` guardan el
`odoo_id` asignado por el ERP. Todo es **idempotente**: sincronizar dos veces
no duplica productos ni clientes. En una fase anterior se comprobó contra un
servidor Odoo real (pedidos de venta S00001, S00002...); en la revisión actual
solo se han ejecutado sus pruebas unitarias.

Para levantar el servidor Odoo de pruebas: `bash scripts/arrancar_odoo.sh`.

`PedidoActualizadoEvent` solicita sincronizar tras confirmar pago, firma o
logística. `PedidoOdooEventListener` lo procesa **AFTER_COMMIT**, y el servicio
Odoo usa una transacción nueva y bloqueo por pedido. Un fallo del ERP no
deshace el pago ni la firma; queda pendiente para reintento manual. La nota de
`sale.order` contiene un único bloque de estado de firma, conservando las notas
manuales. Usa delimitadores de texto porque Odoo elimina atributos HTML. No
altera el estado comercial, impuestos ni registra cobros reales.

### La máquina de estados (detallada)

```
BORRADOR → PENDIENTE_FIRMA → firma del veterinario asignado y pago confirmado (en cualquier orden) → LISTO_PREPARAR
LISTO_PREPARAR → EN_PREPARACION → PREPARADO (≥1 bulto) → EXPEDIDO (bultos en palet)
sin pago: BORRADOR / PENDIENTE_FIRMA / FIRMADO → CANCELADO (manual o caducidad 72 h)
```

`EstadoPedido` define qué transiciones son posibles; `PedidoService` aplica además las reglas de negocio:

- **Reserva de stock**: al enviar a firma (atómica y concurrente, con actualización condicionada en SQL y transacción).
- **Firma**: solo el veterinario asignado al pedido, con su propia identidad y copia inmutable. El colegiado opcional identifica al responsable; no autentica ni sustituye a la imagen o dibujo.
- **Firma y pago en cualquier orden**: `AutorizacionPedido` decide con reglas fijas si la firma es válida (veterinario asignado + contenido actual) y si el pago es completo; solo con ambas se prepara.
- **Descuento de stock físico**: solo al expedir, una sola vez.

## Seguridad

- **Spring Security** con login por formulario: la contraseña se guarda cifrada con **BCrypt** (nunca en texto claro).
- Los permisos se aplican en dos sitios: por **URL y rol** (`SecurityConfig`) y por **pertenencia a la clínica** en cada servicio. Un veterinario de otra clínica recibe 403 aunque conozca el identificador del pedido.
- Los documentos se descargan solo con sesión autorizada: no hay enlaces públicos.
- Simplificación académica: CSRF desactivado para simplificar el frontend (en producción se activaría).
- Sin secretos incrustados: la base de datos y la clave del webhook de pagos se configuran por variables de entorno con valores de prueba por defecto.

## Informes

Los informes (productos más pedidos, evolución mensual, stock bajo, tiempos medios, bultos por destino) se calculan con consultas SQL/JPQL sobre los datos reales de las tablas. Son la base para conectar **Power BI** después, conectándolo directamente a MySQL.
