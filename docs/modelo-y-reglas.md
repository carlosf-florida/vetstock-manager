# Modelo de datos, estados y reglas de negocio

## Tablas principales

| Tabla | Para qué sirve | Relaciones |
|---|---|---|
| `usuarios` | Cuenta, contraseña (BCrypt) y rol: ADMIN, VETERINARIO o AUXILIAR | — |
| `clinicas` | Clínicas cliente | `veterinario_responsable_id` → `veterinarios`; `destino_habitual_id` → `destinos` (salida habitual) |
| `veterinarios` | Veterinario de una clínica (nº de colegiado) | 1:1 `usuarios`, N:1 `clinicas` |
| `auxiliares` | Auxiliar de una clínica | 1:1 `usuarios`, N:1 `clinicas` |
| `productos` | Referencia única, nombre, descripción, precio, `peso_unitario_kg` (opcional; NULL = desconocido), `stock_actual`, `stock_reservado`, `stock_minimo` | — |
| `pedidos` | Estado, veterinario firmante, creador, último modificador, IVA, fechas de cada hito y salida de expedición (`destino_id`, `destino_manual`, quién y cuándo) | N:1 `clinicas`, `veterinarios`, `usuarios`, `destinos` |
| `lineas_pedido` | Producto, cantidad y **precio copiado** al crear la línea | N:1 `pedidos`; único (pedido, producto) |
| `firmas` | Firmante, tipo, imagen, fecha y **copia JSON inmutable** del contenido autorizado | 1:1 `pedidos` |
| `pagos` | Intentos de pago: referencia única, importe, moneda, estado e intentos de notificación | N:1 `pedidos` |
| `destinos` | Salidas de expedición: `codigo` opcional y único (p. ej. «14») y nombre | |
| `palets`, `bultos` | Logística: bulto → pedido; bulto → palet → salida | |
| `documentos` | PDF de pedido, factura o albarán (de prueba) | N:1 `pedidos` |
| `movimientos_stock` | Entradas y salidas, incluida la salida al expedir | |
| `odoo_log` | Registro de la integración opcional con Odoo | |

Stock **disponible** = `stock_actual - stock_reservado` (se calcula, no se guarda).

Scripts: `database/migrations/001_esquema_inicial.sql` crea el esquema actual
completo con datos ficticios y se puede volver a ejecutar para reiniciar la
demo. Las migraciones `002`–`007` actualizan bases antiguas sin borrar datos, y
`008` crea las vistas para Power BI, `009` añade la devolución pendiente y `010` las salidas de expedición y `011` el peso de los productos.

## Veterinario responsable de cada clínica

Cada clínica tiene un veterinario responsable que se configura en
**Administración → Clínicas** (`PUT /api/clinicas/{id}/responsable`, solo ADMIN). Debe
pertenecer a la misma clínica. Los pedidos que crean sus usuarios toman
automáticamente a ese responsable como firmante asignado, y una clínica sin
responsable no puede crear pedidos. Administración, al crear un pedido en nombre de
una clínica, puede elegir otro veterinario de esa misma clínica. El nº de colegiado
es opcional e identificativo: no autentica ni sustituye a la firma.

## Estados del pedido

```
BORRADOR ──enviar a firma (reserva stock)──► PENDIENTE_FIRMA
Firma y pago en cualquier orden:
  PENDIENTE_FIRMA ──firma──► FIRMADO (pendiente de pago) ──pago confirmado──► LISTO_PREPARAR
  PENDIENTE_FIRMA ──pago confirmado──► PENDIENTE_FIRMA (pagado) ──firma──► LISTO_PREPARAR
LISTO_PREPARAR ──► EN_PREPARACION ──(≥1 bulto)──► PREPARADO ──(bultos en palet)──► EXPEDIDO
Sin pago: BORRADOR / PENDIENTE_FIRMA / FIRMADO ──cancelar o caducar──► CANCELADO (libera la reserva)
Sin firma válida: el veterinario asignado ──rechaza──► CANCELADO (si estaba pagado: devolución pendiente)
```

La firma y el pago también se muestran por separado (Firma: Pendiente/Firmada;
Pago: Pendiente/Fallido/Pagado). Cada hito guarda su fecha, lo que permite
medir tiempos en los informes.

Estados de un intento de pago: `PENDIENTE` → `CONFIRMADO` o `FALLADO`. Un
pedido puede tener varios intentos, pero solo uno PENDIENTE a la vez.

## Firma válida, pago completo y clasificación

Se calculan en `backend/.../service/AutorizacionPedido.java` a partir de los datos
guardados (no se almacenan, así nunca se desincronizan):

| Concepto | Regla (datos reales) |
|---|---|
| **Firma válida** | Hay fila en `firmas`, su `usuario_id` es el del veterinario asignado (`pedidos.veterinario_id`) y la copia `contenido_pedido` coincide con las líneas, cantidades, precios y total actuales |
| **Pago completo** | `pedidos.fecha_pago` y un pago `CONFIRMADO` en `pagos` por el **total exacto**. Una factura, un pago pendiente o uno por menos importe no cuentan |
| **Pagados pendientes de firma** | Pago completo, sin firma válida, no cancelado. Se muestra el motivo («Falta la firma de…», «Firmado por X, que no es el veterinario asignado», «contenido anterior») |
| **Firmados y pagados** | Firma válida y pago completo. «Listo para preparar» solo si el estado es `LISTO_PREPARAR` (no cuenta si ya está en preparación, preparado o expedido) |
| **Otros pendientes** | Todo lo demás: borradores, sin pago completo, cancelados |

El veterinario ve en **Mis pedidos → Firma: «Falta la firma del veterinario»** todos los pedidos de
su clínica que necesitan su firma válida, los haya hecho quien los haya hecho (columna «Realizado por»),
aunque su estado logístico sea «Listo para preparar» (se marcan «Bloqueado»).

Los pedidos antiguos firmados por una auxiliar (regla anterior) aparecen como
«Pagados pendientes de firma»: el veterinario asignado puede firmarlos y su firma
sustituye a la no válida (una sola firma por pedido).

## Reglas de negocio

1. **Ningún pedido pasa a preparación sin firma VÁLIDA del veterinario asignado y pago completo confirmado.** Se comprueba al cambiar de estado, no por el nombre del estado.
2. Solo firma el **veterinario asignado**, con su cuenta. Ni auxiliares, ni otros veterinarios, ni administración.
3. La firma exige marcar que se ha revisado el contenido. Se guarda una copia JSON de líneas, precios, IVA, total, firmante y fecha. Una imagen subida no basta por sí sola, y no se reutilizan firmas anteriores.
4. Tras enviar a firma no se pueden cambiar líneas; tras firmar, el contenido queda fijado en la copia.
5. Se puede **pagar antes o después de la firma** (desde que el pedido se envía a firma). Administración no paga en nombre de la clínica.
6. El pago se confirma únicamente por la **notificación del proveedor** firmada con HMAC. Se comprueban la referencia, el importe (contra el total del pedido) y la moneda. Las notificaciones repetidas no repiten efectos. Volver a la página de éxito no marca nada.
7. Un pago fallido permite reintentar con una referencia nueva. Si ya hay uno PENDIENTE, se reutiliza (no hay cobros dobles).
8. Precios e importes los calcula siempre el servidor (IVA del 4 % del prototipo, redondeo por línea).
9. **Stock:** se reserva al enviar a firma, en una sola sentencia SQL condicionada (sin sobreventa en concurrencia). Al expedir se descuenta el físico y se libera la reserva, una sola vez.
10. **Caducidad (decisión acordada):** cada 15 minutos, los pagos PENDIENTES de más de 60 min pasan a FALLADO. Los pedidos enviados a firma sin pago confirmado durante **72 h** se cancelan y liberan la reserva, con el motivo guardado. Un pedido con un pago en curso reciente no caduca en esa pasada.
11. **Un pedido pagado no se cancela desde la aplicación.** Solo el veterinario asignado puede **rechazarlo** mientras no lo haya firmado: se cancela, libera stock y queda «devolución pendiente» (el dinero se devuelve fuera de la aplicación; no hay reembolsos en el alcance).
12. **Logística:** PREPARADO exige al menos un bulto y una salida: si el pedido no la tiene, toma la salida habitual de la clínica; si la clínica tampoco tiene, se rechaza. Los bultos se calculan por peso (máx. 25 kg por bulto, mínimo 1); al preparar se crean los registros que falten y, si falta algún peso o sobran registros, no se prepara. EXPEDIDO exige registros = calculados y todos en un palet. Un bulto solo va en un palet de la misma salida que su pedido. La salida solo la cambia administración y nunca en un pedido expedido o cancelado. Un palet expedido no admite bultos. Detalle en [logistica-salidas-bultos.md](logistica-salidas-bultos.md).
13. Documentos: solo PDF reales (cabecera `%PDF-`) de 10 MB como máximo. Los sube administración y los descarga la clínica del pedido.
