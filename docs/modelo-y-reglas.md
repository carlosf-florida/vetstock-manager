# Modelo de datos, estados y reglas de negocio

## Tablas principales

| Tabla | Para qué sirve | Relaciones |
|---|---|---|
| `usuarios` | Cuenta, contraseña (BCrypt) y rol: ADMIN, VETERINARIO o AUXILIAR | — |
| `clinicas` | Clínicas cliente | `veterinario_responsable_id` → `veterinarios` |
| `veterinarios` | Veterinario de una clínica (nº de colegiado) | 1:1 `usuarios`, N:1 `clinicas` |
| `auxiliares` | Auxiliar de una clínica | 1:1 `usuarios`, N:1 `clinicas` |
| `productos` | Referencia única, nombre, descripción, precio, `stock_actual`, `stock_reservado`, `stock_minimo` | — |
| `pedidos` | Estado, veterinario firmante, creador, último modificador, IVA y fechas de cada hito | N:1 `clinicas`, `veterinarios`, `usuarios` |
| `lineas_pedido` | Producto, cantidad y **precio copiado** al crear la línea | N:1 `pedidos`; único (pedido, producto) |
| `firmas` | Firmante, tipo, imagen, fecha y **copia JSON inmutable** del contenido autorizado | 1:1 `pedidos` |
| `pagos` | Intentos de pago: referencia única, importe, moneda, estado e intentos de notificación | N:1 `pedidos` |
| `destinos`, `palets`, `bultos` | Logística: bulto → pedido; bulto → palet → destino | |
| `documentos` | PDF de pedido, factura o albarán (de prueba) | N:1 `pedidos` |
| `movimientos_stock` | Entradas y salidas, incluida la salida al expedir | |
| `odoo_log` | Registro de la integración opcional con Odoo | |

Stock **disponible** = `stock_actual - stock_reservado` (se calcula, no se guarda).

Scripts: `database/migrations/001_esquema_inicial.sql` crea el esquema actual
completo con datos ficticios y se puede volver a ejecutar para reiniciar la
demo. Las migraciones `002`–`007` actualizan bases antiguas sin borrar datos, y
`008` crea las vistas para Power BI.

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
PENDIENTE_FIRMA ──firma del veterinario asignado──► FIRMADO (pendiente de pago)
FIRMADO ──notificación de pago CONFIRMADO verificada──► LISTO_PREPARAR
LISTO_PREPARAR ──► EN_PREPARACION ──(≥1 bulto)──► PREPARADO ──(bultos en palet)──► EXPEDIDO
BORRADOR / PENDIENTE_FIRMA / FIRMADO (sin pago) ──cancelar o caducar──► CANCELADO (libera la reserva)
```

La firma y el pago también se muestran por separado (Firma: Pendiente/Firmada;
Pago: Pendiente/Fallido/Pagado). Cada hito guarda su fecha, lo que permite
medir tiempos en los informes.

Estados de un intento de pago: `PENDIENTE` → `CONFIRMADO` o `FALLADO`. Un
pedido puede tener varios intentos, pero solo uno PENDIENTE a la vez.

## Reglas de negocio

1. **Ningún pedido pasa a preparación sin firma del veterinario asignado y pago confirmado.** Se comprueba al cambiar de estado, no solo por el nombre del estado.
2. Solo firma el **veterinario asignado**, con su cuenta. Ni auxiliares, ni otros veterinarios, ni administración.
3. La firma exige marcar que se ha revisado el contenido. Se guarda una copia JSON de líneas, precios, IVA, total, firmante y fecha. Una imagen subida no basta por sí sola, y no se reutilizan firmas anteriores.
4. Tras enviar a firma no se pueden cambiar líneas; tras firmar, el contenido queda fijado en la copia.
5. Solo se puede **pagar un pedido FIRMADO**. Administración no paga en nombre de la clínica.
6. El pago se confirma únicamente por la **notificación del proveedor** firmada con HMAC. Se comprueban la referencia, el importe (contra el total del pedido) y la moneda. Las notificaciones repetidas no repiten efectos. Volver a la página de éxito no marca nada.
7. Un pago fallido permite reintentar con una referencia nueva. Si ya hay uno PENDIENTE, se reutiliza (no hay cobros dobles).
8. Precios e importes los calcula siempre el servidor (IVA del 4 % del prototipo, redondeo por línea).
9. **Stock:** se reserva al enviar a firma, en una sola sentencia SQL condicionada (sin sobreventa en concurrencia). Al expedir se descuenta el físico y se libera la reserva, una sola vez.
10. **Caducidad (decisión acordada):** cada 15 minutos, los pagos PENDIENTES de más de 60 min pasan a FALLADO. Los pedidos enviados a firma sin pago confirmado durante **72 h** se cancelan y liberan la reserva, con el motivo guardado. Un pedido con un pago en curso reciente no caduca en esa pasada.
11. **Un pedido pagado no se cancela desde la aplicación** (no hay reembolsos en el alcance).
12. **Logística:** PREPARADO exige al menos un bulto. EXPEDIDO exige todos los bultos asignados a un palet (y, por tanto, a un destino). Un palet expedido no admite bultos.
13. Documentos: solo PDF reales (cabecera `%PDF-`) de 10 MB como máximo. Los sube administración y los descarga la clínica del pedido.
