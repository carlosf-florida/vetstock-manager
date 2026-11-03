# VetStock Manager · Contrato de la API REST

Autenticación por **sesión HTTP** (cookie `JSESSIONID`). Todas las rutas `/api/**` requieren sesión salvo las públicas. El backend comprueba rol y pertenencia a la clínica.

Roles: `ADMIN` (administración: gestiona, ayuda a crear pedidos, no firma ni paga), `VETERINARIO` (firma solo los pedidos que tiene asignados), `AUXILIAR` (crea pedidos de su clínica, los deja pendientes de firma y puede pagarlos una vez firmados). La firma es simple, sin validez legal certificada.

HTTP: `200` OK · `201` creado · `400` regla de negocio/datos inválidos · `401` sin sesión · `403` sin permiso · `404` inexistente. Un fallo de integración Odoo puede devolver HTTP 200 con `exito: false`; la interfaz debe comprobar ambos.

## Sesión

| Método y ruta | Quién | Notas |
|---|---|---|
| `POST /api/auth/login` | público | FORMULARIO `application/x-www-form-urlencoded`, `username` y `password`; no JSON |
| `POST /api/auth/logout` | sesión | Cierra la sesión |
| `GET /api/auth/me` | sesión | Cuenta, rol y clínica |

## Productos y stock

- `GET /api/productos?busqueda=&categoria=&soloBajoStock=` y `GET /api/productos/{id}`: sesión; stock disponible calculado en backend.
- `POST /api/productos`: ADMIN, `{codigo,nombre,descripcion,categoria,precio,stockInicial,stockMinimo}` → 201.
- `PUT /api/productos/{id}` y `PATCH /api/productos/{id}/activar` o `/desactivar`: ADMIN.
- `POST /api/stock/entrada` o `/salida`: ADMIN, `{productoId,cantidad,motivo}`. No permite retirar stock reservado.
- `GET /api/stock/movimientos?productoId=`: ADMIN.

## Clínicas y cuentas

- `GET /api/clinicas` y `POST /api/clinicas` `{nombre,direccion,telefono}`: ADMIN.
- `PUT /api/clinicas/{id}/responsable` `{veterinarioId}`: ADMIN; el veterinario debe pertenecer a la clínica.
- `GET /api/veterinarios`: ADMIN ve todos; el cliente autenticado ve los de su clínica.
- `POST /api/veterinarios` `{username,password,nombreCompleto,clinicaId,numeroColegiado,telefono}`: ADMIN.
- `GET /api/auxiliares` y `POST /api/auxiliares` `{username,password,nombreCompleto,clinicaId}`: ADMIN.

## Pedidos

| Método y ruta | Quién | Reglas |
|---|---|---|
| `GET /api/pedidos?estado=&clinicaId=` | sesión | ADMIN todos; clientes solo su clínica |
| `GET /api/pedidos/{id}` | sesión | Acceso a clínica comprobado |
| `POST /api/pedidos` | ADMIN, VETERINARIO, AUXILIAR | `{observaciones (≤255),lineas:[{productoId,cantidad≥1}]}`; responsable automático para clientes. ADMIN añade `clinicaId` y `veterinarioId`. Precio calculado por backend; 201 BORRADOR |
| `PUT /api/pedidos/{id}/lineas` | creador o ADMIN | Solo BORRADOR; guarda `modificadoPorNombre` y `fechaModificacion` |
| `PATCH /api/pedidos/{id}/enviar-firma` | ADMIN o su clínica | Confirma el pedido, reserva stock, pasa a PENDIENTE_FIRMA |
| `PATCH /api/pedidos/{id}/firmar` | solo el VETERINARIO asignado al pedido (resto: 403); antes o después del pago; sustituye una firma no válida | `{tipoFirma:"DIBUJO"/"IMAGEN",imagenBase64,nombreArchivo?,confirmacion:true,numeroColegiado?}`. Solo en PENDIENTE_FIRMA → FIRMADO. Guarda firmante, fecha y copia inmutable; rechaza firma repetida |
| `GET /api/pedidos/{id}/firma` | ADMIN o su clínica | Responsable y firmante separados; colegiado identificativo opcional |
| `GET /api/pedidos/{id}/firma/imagen` | ADMIN o su clínica | Imagen PNG/JPG |
| `PATCH /api/pedidos/{id}/cancelar` | ADMIN o su clínica | Libera la reserva y guarda `motivoCancelacion`. Un pedido pagado no se cancela (nadie) |
| `GET /api/pagos?pedidoId={id}` | ADMIN o su clínica | Intentos de pago, del más reciente al más antiguo; un intento pendiente puede continuarse con su referencia |

Firma con ratón o imagen PNG/JPG de hasta 5 MB. `numeroColegiado` es opcional, debe coincidir si se informa y **no sustituye a la firma ni autentica**. 

`PedidoResponse` incluye `creadoPorNombre`, `modificadoPorNombre`, `fechaModificacion`, `motivoCancelacion`, `estadoFirma` (PENDIENTE/FIRMADO), `estadoPago` (PENDIENTE/PAGADO), `firmanteNombre`, `firmanteRol`, `veterinarioUsername`, `numeroColegiadoResponsable`, `odooId`, `odooSincronizacionPendiente` y `odooUltimoError`.

También devuelve `baseImponible`, `ivaPorcentaje`, `importeIva` y `moneda` (EUR). `total` es **base más IVA**, calculado por el backend. Los precios unitarios e importes de líneas son netos. Pedidos nuevos: IVA del 4% configurado para el prototipo, con redondeo por línea HALF_UP. Históricos anteriores a la migración 005: conservan 0% y el importe original. No se aceptan tasas ni importes calculados por el navegador. Ver `docs/iva-pedidos.md`.

`FirmaResponse` incluye responsable (`veterinarioNombre`, `veterinarioUsername`) separado de `firmanteNombre`, `firmanteUsername`, `firmanteRol`, `numeroColegiado`.

## Estados operativos, firma y pago

```text
BORRADOR → PENDIENTE_FIRMA → firma y pago en cualquier orden → LISTO_PREPARAR
LISTO_PREPARAR → EN_PREPARACION → PREPARADO (≥1 bulto) → EXPEDIDO (bultos en palet)
BORRADOR / PENDIENTE_FIRMA / FIRMADO sin pago → CANCELADO (manual o caducidad de 72 h)
Sin firma válida → CANCELADO por rechazo del veterinario asignado (pagado: devolucionPendiente = true)
```

Solo firma VÁLIDA (veterinario asignado + contenido actual) y pago completo habilitan la preparación; el stock físico solo se descuenta al expedir. CANCELADO es final.

Cada `PedidoResponse` de `GET /api/pedidos` y `GET /api/pedidos/{id}` incluye `autorizacion`:
`{firmaValida, motivoFirma, pagoCompleto, importePendiente, clasificacion, listoParaPreparar}`,
con `clasificacion` = `PAGADO_PENDIENTE_FIRMA` | `FIRMADO_Y_PAGADO` | `OTROS` (ver `docs/modelo-y-reglas.md`).

| Ruta | Quién | Notas |
|---|---|---|
| `PATCH /api/pedidos/{id}/rechazar` | solo el VETERINARIO asignado | `{motivo}` (1–200 caracteres). Solo si no lo ha firmado él. Cancela, libera stock y, si estaba pagado, `devolucionPendiente = true` |

## Pagos (adaptador de DEMOSTRACIÓN)

- `POST /api/pagos/{pedidoId}/iniciar`: solo usuarios de la clínica del pedido (ADMIN → 403); el pedido debe estar PENDIENTE_FIRMA o FIRMADO y sin pago confirmado (si no, 400). 201 `{referencia,importe,moneda,proveedor:"SIMULADO",urlSimulada}`. `importe` incluye IVA; `urlSimulada` usa `/pago-simulado/{referencia}`. Si ya existe un intento pendiente, retomarlo en esa ruta sin crear otro. Reintento tras fallo con referencia nueva.
- `POST /api/pagos/simulado/{referencia}` `{resultado:"CONFIRMADO"/"FALLADO"}`: con sesión de la clínica del pedido. Simula la página del banco y envía al webhook una notificación firmada; devuelve el estado REAL del pago. Se desactiva con `PAGOS_SIMULADO_ACTIVO=false`. No es una integración bancaria.
- `POST /api/pagos/notificacion` (webhook): sin sesión, autenticado con la cabecera `X-Firma-Notificacion` = Base64(HMAC-SHA256(`PAGOS_SECRETO_WEBHOOK`, cuerpo exacto)). Cuerpo `{referencia,importe,moneda,resultado}`. Firma ausente o mala → 403. Importe distinto del total o moneda distinta de EUR → intento FALLADO y 400. Ya resuelto → 200 sin efectos (idempotente). CONFIRMADO → pedido LISTO_PREPARAR y exportación a Odoo tras el commit.
- Los intentos PENDIENTES de más de 60 min pasan a FALLADO automáticamente.
- `GET /api/pagos?estado=`: ADMIN; con `pedidoId` también la propia clínica.

## Odoo / ERP

Rutas manuales solo ADMIN:

| Ruta | Función |
|---|---|
| `GET /api/odoo/estado` | Modo y contadores |
| `POST /api/odoo/probar-conexion` | Probar conexión |
| `POST /api/odoo/sincronizar/productos` | Crea/actualiza textos por código, sin duplicar |
| `POST /api/odoo/sincronizar/clientes` | Crea/actualiza contactos por ID guardado; acentos y responsable |
| `POST /api/odoo/pedidos/{id}/exportar` | Crea o actualiza pedido PAGADO, incluso por firmar; permite reintento y conserva ID |
| `GET /api/odoo/log` | Últimas 50 operaciones |

El pago confirmado, la firma posterior y las transiciones de logística solicitan automáticamente actualizar Odoo después del commit local. El resumen del pedido en `sale.order.note` muestra «Por firmar»/«Firmado», responsable y firmante real/rol. Cuando el pedido está pagado y con firma válida se confirma en Odoo (`action_confirm`: presupuesto → pedido de venta), solo si estaba en presupuesto; no reactiva pedidos cancelados ni registra cobros reales. Referencia `client_order_ref=VSM-{id}`. Fallar Odoo no deshace pagos o firmas; queda pendiente para reintento manual. Sin sincronización bidireccional ni reintentos periódicos aún.

## Logística, documentos, informes y dashboard

- ADMIN: `PATCH /api/pedidos/{id}/iniciar-preparacion`, `/preparar` (exige peso completo, asigna la salida habitual si el pedido no tiene y crea los bultos calculados que falten como `VSM-{id}-B{n}`; 400 sin salida posible, si falta algún peso o si sobran registros), `/expedir` (exige registros = calculados y todos en un palet); todas exigen firma y pago.
- ADMIN: `GET /api/bultos?pedidoId=&paletId=`, `POST /api/bultos` `{identificador,pedidoId}` (400 si falta algún peso o ya están todos los calculados; no en EXPEDIDO), `DELETE /api/bultos/{id}` (204; solo un sobrante sin palet de un pedido no expedido), `PATCH /api/bultos/{id}/asignar-palet` `{paletId}`.
- ADMIN: `PATCH /api/pedidos/{id}/salida` `{destinoId}`: salida manual; 400 si está EXPEDIDO/CANCELADO o si tiene bultos en un palet de otra salida.
- ADMIN: `PATCH /api/bultos/{id}/asignar-palet` exige que el palet tenga la misma salida que el pedido.
- ADMIN: `GET /api/palets`, `POST /api/palets` `{identificador,destinoId}`, `GET /api/palets/{id}` (con sus bultos), `PATCH /api/palets/{id}/expedir`. Un palet expedido no admite bultos.
- ADMIN: `GET /api/destinos`, `POST /api/destinos` y `PUT /api/destinos/{id}` `{codigo?,nombre}` (código único). Respuesta: `{id,codigo,nombre,etiqueta}`.
- ADMIN: `PUT /api/clinicas/{id}/salida-habitual` `{destinoId|null}`; no cambia pedidos ya preparados.
- Pedidos: `salidaId`, `salida`, `salidaManual`, `salidaAsignadaPor`, `fechaAsignacionSalida`, `salidaHabitualClinica`, `bultos` (registros), `pesoTotalKg`, `bultosCalculados` (null si faltan pesos) y `productosSinPeso` solo llegan con datos a ADMIN; a la clínica le llegan vacíos.
- Productos: `pesoUnitarioKg` opcional (> 0, máx. 3 decimales). `stockInicial` solo es obligatorio al crear.
- ADMIN: `POST /api/pedidos/{id}/documentos`, multipart PDF ≤10 MB con `archivo` y `tipo` PEDIDO/FACTURA/ALBARAN.
- ADMIN o su clínica: `GET /api/pedidos/{id}/documentos`, `GET /api/documentos/{id}/descargar`.
- ADMIN: `GET /api/informes/productos-top?limite=10`, `/pedidos-por-mes`, `/stock-bajo`, `/pendientes`, `/tiempos`, `/destinos`.
- Sesión: `GET /api/dashboard`, datos según rol.
