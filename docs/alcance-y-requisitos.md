# Alcance y requisitos

## Descripción

VetStock Manager centraliza los pedidos de material veterinario entre las
clínicas y una distribuidora ficticia: catálogo y stock, pedidos con firma de
autorización del veterinario, pago obligatorio confirmado automáticamente,
documentos, preparación y expedición (bultos por peso, palets y salidas) e informes.

No es un ERP completo. El alcance es el de un proyecto individual con un
**recorrido principal que funciona de principio a fin** y está cubierto por
pruebas automáticas.

## Usuarios y permisos

Relación de datos: cada **usuario** tiene un rol. Un **veterinario** o un
**auxiliar** pertenece a una **clínica**. Cada clínica tiene un **veterinario
responsable**. Al crear un pedido, el **veterinario firmante asignado** es el
responsable de la clínica (o el que elija administración entre los de esa
clínica). Todos los permisos se comprueban en el backend.

| Acción | Administración | Auxiliar | Veterinario |
|---|---|---|---|
| Ver catálogo | Sí | Sí | Sí |
| Crear pedido | Sí, en nombre de una clínica (queda la autoría) | Sí, de su clínica | Sí, de su clínica |
| Modificar líneas de un borrador | Sí (queda quién y cuándo) | Si lo creó | Si lo creó |
| Ver pedidos | Todos | Solo de su clínica | Solo de su clínica |
| Firmar | **No** | **No** | Solo los pedidos que tiene asignados |
| Pagar | **No** (lo hace la clínica) | Sí, antes o después de la firma | Sí, antes o después de la firma |
| Rechazar un pedido | No | No | Solo los suyos, si no los ha firmado |
| Confirmar un pago a mano | **Nadie**: solo la notificación verificada del proveedor | — | — |
| Preparación, bultos, palets, expedición | Sí | No | No |
| Subir documentos PDF | Sí | No | No |
| Descargar documentos | Todos | Solo de su clínica | Solo de su clínica |
| Clínicas, usuarios, productos, stock, informes | Sí | No | No |

## Requisitos funcionales y estado real

Estado: **Hecho** = implementado y con prueba automática; **Hecho (manual)** =
implementado y comprobado a mano en el navegador; **Parcial**; **Pendiente**.

| ID | Requisito | Estado |
|---|---|---|
| RF-01 | Inicio y cierre de sesión con roles | Hecho |
| RF-02 | Separación de datos entre clínicas | Hecho |
| RF-03 | Catálogo con búsqueda, categoría y precio | Hecho (manual) |
| RF-04 | Carrito, revisión y creación de pedido; precios calculados en el servidor | Hecho |
| RF-05 | Administración crea o modifica pedidos en nombre de una clínica y queda la autoría | Hecho |
| RF-06 | Enviar a firma reserva stock de forma atómica (sin sobreventa) | Hecho |
| RF-07 | Firma del veterinario asignado (dibujo o PNG/JPG ≤ 5 MB) tras revisar el contenido; copia inmutable | Hecho |
| RF-08 | Bloqueo de productos y cantidades desde el envío a firma | Hecho |
| RF-09 | Pago obligatorio (antes o después de la firma), por pasarela **de demostración** | Hecho |
| RF-25 | Separación de pedidos por paso del trabajo (pagados pendientes de firma, listos, en preparación, preparados, expedidos y otros) con reglas fijas (firma válida + pago completo) | Hecho |
| RF-26 | Rechazo del veterinario asignado con motivo; devolución pendiente si estaba pagado | Hecho (la devolución es manual, fuera de la app) |
| RF-27 | Mismas listas en Odoo Ventas, una por estado, con preparados y expedidos agrupados por salida (campos x_vetstock_*, menús y favoritos) | Hecho (solo pedidos pagados, que son los que se exportan) |
| RF-10 | Confirmación del pago solo por notificación autenticada (HMAC), con validación de importe y moneda e idempotencia | Hecho |
| RF-11 | Reintento de pagos fallidos sin duplicar cobros | Hecho |
| RF-12 | Preparación solo con firma y pago | Hecho |
| RF-13 | Palets de una salida: asignar y sacar bultos, y ver su contenido | Hecho (marcar un palet como expedido desde la pantalla está pendiente; el endpoint existe) |
| RF-14 | Expedido exige que los bultos registrados coincidan con los calculados y estén todos en un palet | Hecho |
| RF-15 | Expedir descuenta el stock físico una sola vez | Hecho |
| RF-16 | Caducidad de reservas sin pago (72 h) | Hecho |
| RF-17 | Documentos PDF ≤ 10 MB con permisos | Hecho |
| RF-18 | Búsqueda de pedidos por número, clínica, fecha y estado; firma y pago visibles | Hecho (manual) |
| RF-19 | Gestión de clínicas, veterinarios, auxiliares y responsable | Parcial: alta y asignación; no hay edición ni baja de usuarios |
| RF-20 | Productos (con peso por unidad), movimientos de stock y avisos de stock bajo | Hecho (manual; edición del peso con prueba automática) |
| RF-21 | Informes con datos reales (gráficos propios en React) | Hecho (manual) |
| RF-22 | Datos preparados para Power BI (vistas SQL) | Hecho; conexión con Power BI pendiente de licencia (ver `power-bi.md`) |
| RF-23 | Integración con Odoo (opcional, adaptador): exporta sin duplicar y permite reintentar si falla | Hecho (comprobado en vivo; no forma parte del recorrido principal) |
| RF-24 | Pasarela real (Redsys/Bizum) | Pendiente: requiere contrato y credenciales (ver `firma-y-pagos.md`) |
| RF-28 | Salida de expedición: habitual de cada clínica, asignada al preparar; solo administración la cambia, nunca en un pedido expedido | Hecho (ver `logistica-salidas-bultos.md`) |
| RF-29 | Peso del pedido y bultos calculados (hasta 25 kg por bulto, mínimo 1); al preparar se crean los que falten y sin peso completo no se prepara | Hecho (pesos de la demo ficticios; ver `logistica-salidas-bultos.md`) |

## Requisitos no funcionales

- Contraseñas con BCrypt y sesión HTTP. CSRF desactivado por simplicidad (ver pendientes).
- Dinero con `BigDecimal` / `DECIMAL(10,2)` y pesos con `BigDecimal` / `DECIMAL(10,3)`. Cantidades enteras positivas.
- Concurrencia: reserva con `UPDATE ... WHERE disponible >= cantidad`, bloqueo
  optimista (`@Version`) en pedidos y bloqueo pesimista al procesar notificaciones de pago.
- Interfaz responsive, con estados de carga, error y listas vacías, y estados también en texto.
- Configuración por variables de entorno (`.env.example`), sin secretos reales en el repositorio.

## Fuera de alcance

Facturación fiscal válida, reembolsos, cancelación de pedidos pagados,
firma con certificado cualificado, integración con escáneres y despliegue público.
