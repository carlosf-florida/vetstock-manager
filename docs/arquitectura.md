# VetStock Manager · Arquitectura y patrones de diseño

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

El enunciado exige que el pago sea una integración real. En este prototipo:

1. El backend crea el intento de pago con **referencia única** y **importe calculado por el servidor**.
2. La "pantalla del banco" está simulada en el propio frontend (`/pago-simulado/:referencia`).
3. El resultado llega por `POST /api/pagos/notificacion` (el webhook que en producción llamaría el proveedor), autenticado con un **token** en la cabecera.
4. La notificación **valida importe y moneda** y es **idempotente**: si llega dos veces, la segunda no tiene efecto.
5. Una notificación `CONFIRMADO` registra el pago y solicita exportar a Odoo después del commit. Solo avanza a `LISTO_PREPARAR` si existe también una firma interna. El backend nunca se fía de la pantalla del navegador.

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
no duplica productos ni clientes. Esta integración se ha verificado de extremo
a extremo contra un servidor Odoo real (los pedidos exportados aparecen como
pedidos de venta S00001, S00002... en el ERP).

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
BORRADOR → PENDIENTE_FIRMA
  firma primero → FIRMADO → pago → LISTO_PREPARAR
  pago primero  → PENDIENTE_FIRMA (pagado) → firma → LISTO_PREPARAR
LISTO_PREPARAR → EN_PREPARACION → PREPARADO → EXPEDIDO
```

`EstadoPedido` define qué transiciones son posibles; `PedidoService` aplica además las reglas de negocio:

- **Reserva de stock**: al enviar a firma (atómica y concurrente, con actualización condicionada en SQL y transacción).
- **Firma**: solo el veterinario asignado al pedido, con su propia identidad y copia inmutable. El colegiado opcional identifica al responsable; no autentica ni sustituye a la imagen o dibujo.
- **Firma y pago completados**: ambos necesarios para `LISTO_PREPARAR`, independientemente de su orden.
- **Descuento de stock físico**: solo al expedir, una sola vez.

## Seguridad

- **Spring Security** con login por formulario: la contraseña se guarda cifrada con **BCrypt** (nunca en texto claro).
- Los permisos se aplican en dos sitios: por **URL y rol** (`SecurityConfig`) y por **pertenencia a la clínica** en cada servicio. Un veterinario de otra clínica recibe 403 aunque conozca el identificador del pedido.
- Los documentos se descargan solo con sesión autorizada: no hay enlaces públicos.
- Simplificación académica: CSRF desactivado para simplificar el frontend (en producción se activaría).
- Sin secretos incrustados: la base de datos y el token de pagos se configuran por variables de entorno con valores de prueba por defecto.

## Informes

Los informes (productos más pedidos, evolución mensual, stock bajo, tiempos medios, bultos por destino) se calculan con consultas SQL/JPQL sobre los datos reales de las tablas. Son la base para conectar **Power BI** después, conectándolo directamente a MySQL.
