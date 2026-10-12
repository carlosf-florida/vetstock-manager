# Product Backlog inicial — VetStock Manager

| ID | Historia de usuario | Prioridad | Story Points | Estado |
|---|---|---|---:|---|
| US-01 | Como responsable de almacén, quiero iniciar sesión para acceder de forma segura al panel de gestión. | Alta | 3 | Sprint 1 |
| US-02 | Como responsable de almacén, quiero consultar los productos y el stock disponible para saber qué productos puedo incluir en los pedidos. | Alta | 3 | Sprint 1 |
| US-03 | Como responsable de almacén, quiero registrar veterinarios en el sistema para poder asociarles pedidos. | Alta | 3 | Sprint 1 |
| US-04 | Como responsable de almacén, quiero crear un pedido asociado a un veterinario para registrar una solicitud de productos. | Alta | 5 | Sprint 1 |
| US-05 | Como responsable de almacén, quiero añadir productos y cantidades a un pedido para detallar correctamente la solicitud. | Alta | 5 | Sprint 2 |
| US-06 | Como responsable de almacén, quiero guardar los pedidos con estado pendiente de autorización para controlar los pedidos no confirmados. | Alta | 3 | Sprint 2 |
| US-07 | Como veterinario, quiero acceder a mis pedidos para revisar los productos y las cantidades solicitadas. | Alta | 5 | Sprint 2 |
| US-08 | Como veterinario, quiero autorizar o rechazar un pedido para confirmar digitalmente si es correcto antes del envío. | Alta | 5 | Sprint 2 |
| US-09 | Como responsable de almacén, quiero consultar el estado de cada pedido para saber si está pendiente, autorizado, preparado o enviado. | Media | 3 | Sprint 3 |
| US-10 | Como responsable de almacén, quiero actualizar el estado de un pedido para registrar cuándo se ha preparado o enviado. | Media | 3 | Sprint 3 |
| US-11 | Como responsable de almacén, quiero registrar entradas y salidas de productos para mantener el stock actualizado. | Media | 5 | Sprint 3 |
| US-12 | Como responsable de almacén, quiero ver el historial de pedidos de cada veterinario para consultar solicitudes anteriores. | Baja | 5 | Product Backlog |
| US-13 | Como responsable de almacén, quiero recibir avisos de stock bajo para saber qué productos debo reponer. | Baja | 5 | Product Backlog |
| US-14 | Como responsable de almacén, quiero exportar los datos de stock y pedidos a CSV para poder analizarlos en Excel. | Baja | 5 | Product Backlog |

## Criterios de aceptación

### US-01 — Inicio de sesión
- El formulario incluye email o usuario y contraseña.
- Las credenciales correctas permiten acceder al panel principal.
- Las credenciales incorrectas muestran un mensaje genérico de error.
- Las rutas privadas no se pueden abrir sin iniciar sesión.

### US-02 — Consulta de productos y stock
- Se muestra una lista de productos almacenados en la base de datos.
- Cada producto muestra nombre, referencia, categoría y stock actual.
- Se puede buscar por nombre o referencia.
- Los productos sin stock o con stock bajo se identifican visualmente.

### US-03 — Registro de veterinarios
- Se puede crear un veterinario con nombre, clínica, email y teléfono.
- No se guarda un veterinario sin nombre, clínica o email.
- El email debe tener formato válido.
- No se permite repetir un email ya registrado.

### US-04 — Creación de pedido
- Se puede seleccionar un veterinario previamente registrado.
- El pedido se guarda con identificador único y fecha de creación.
- Se puede añadir una observación opcional.
- No se guarda un pedido sin veterinario asociado.
- El pedido aparece en el listado general de pedidos.

### US-05 — Añadir productos a un pedido
- Se pueden añadir uno o varios productos a un pedido.
- Cada línea muestra producto, cantidad solicitada y stock disponible.
- No se permite una cantidad superior al stock disponible.
- Se puede modificar o eliminar una línea antes de confirmar el pedido.

### US-06 — Pedido pendiente de autorización
- Todo pedido nuevo se crea con estado pendiente de autorización.
- El estado se muestra en el listado de pedidos.
- Se pueden filtrar los pedidos pendientes.
- Se guarda la fecha de creación y el estado inicial.

### US-07 — Consulta de pedidos por veterinario
- El veterinario accede a una pantalla o enlace seguro para consultar sus pedidos.
- Solo puede consultar los pedidos que le pertenecen.
- Cada pedido muestra identificador, fecha, productos, cantidades y estado.
- Puede abrir el detalle de un pedido pendiente.

### US-08 — Autorizar o rechazar pedido
- El veterinario puede autorizar o rechazar un pedido pendiente.
- Al autorizar, el estado cambia a autorizado.
- Al rechazar, el estado cambia a rechazado y permite añadir un comentario.
- Se guarda la fecha y hora de la respuesta.
- Un pedido autorizado no se puede modificar sin dejar registro.
