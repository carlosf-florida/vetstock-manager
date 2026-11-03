# Logística: salidas de expedición y bultos por pedido

Actualizado el 3 de noviembre de 2026 (rama `feature/logistica-salidas-bultos`).

## Qué problema resuelve

Antes, el pedido no sabía por qué **salida de expedición** tenía que salir y los
bultos solo se veían en la pantalla Admin → Bultos. Ahora:

- Cada clínica puede tener una **salida habitual**. Ejemplo confirmado: la
  Clínica Veterinaria San Roque sale por la salida **14 - Regional**.
- Al marcar un pedido como **PREPARADO** se le asigna esa salida (salvo que
  administración ya le haya elegido otra a mano).
- El listado y el detalle del pedido muestran **cuántos bultos** tiene (contados
  de verdad, no un contador aparte) y por qué salida va.
- Odoo recibe los **Bultos** y la **Salida** del pedido, en solo lectura.

No se han inventado más salidas ni relaciones ciudad → salida: solo Regional
tiene código (14). Valencia y Alicante siguen sin código hasta que alguien lo confirme.

## Modelo de datos (migración `010_salidas_expedicion.sql`)

Se reutiliza la tabla `destinos` como «salida de expedición» (los palets ya
apuntaban a ella).

| Tabla | Columna nueva | Para qué |
|---|---|---|
| `destinos` | `codigo` (VARCHAR(10), único, opcional) | Código del almacén, p. ej. `14`. No depende del ID interno |
| `clinicas` | `destino_habitual_id` → `destinos` | Salida habitual de la clínica (opcional) |
| `pedidos` | `destino_id` → `destinos` | Salida del pedido |
| `pedidos` | `destino_manual` | `true` si la eligió administración a mano |
| `pedidos` | `destino_asignado_por_id` → `usuarios`, `fecha_asignacion_destino` | Quién y cuándo la asignó |

La etiqueta que se muestra es `código - nombre` («14 - Regional») o solo el
nombre si no hay código. El **número de bultos** no se guarda: se calcula con
`COUNT` sobre `bultos`, así nunca se desincroniza.

## Reglas

1. **PREPARADO**: si el pedido no tiene salida, toma la salida habitual de su
   clínica. Si la clínica no tiene, el backend **rechaza** el cambio con un
   mensaje claro y el detalle del pedido muestra un aviso con un selector. Nunca
   se elige una salida al azar.
2. **Salida manual**: solo administración (`PATCH /api/pedidos/{id}/salida`).
   Se puede en LISTO_PREPARAR, EN_PREPARACION y PREPARADO; **no** en EXPEDIDO ni
   CANCELADO. Si algún bulto del pedido ya está en un palet de otra salida, se
   rechaza.
3. **Palets**: un bulto solo entra en un palet **de la misma salida** que su
   pedido. Asignar otra vez el mismo palet no cambia nada.
4. Cambiar la salida habitual de una clínica **no toca** pedidos ya preparados o
   expedidos (cada pedido guarda su propia salida).
5. Bultos sin duplicados: el identificador es único (se comprueba antes de
   guardar y en la base de datos).
6. La salida, quién la asignó y los bultos son **logística interna**: la API
   no los devuelve a veterinarios ni auxiliares (`PedidoResponse.sinLogisticaInterna()`).
   Bultos, palets y salidas son solo de ADMIN.
7. Se mantienen las reglas anteriores: preparar exige firma válida del veterinario
   asignado y pago completo; preparado ≠ expedido; el stock se descuenta una sola
   vez, al expedir.

## Pantallas (administración)

- **Salidas** (antes «Destinos»): alta y edición de código y nombre.
- **Clínicas**: columna «Salida habitual» con un selector.
- **Pedidos**: columnas «Salida» («Sin asignar» si no tiene) y «Bultos», y filtro por salida.
- **Detalle del pedido**: tarjeta «Salida de expedición» (salida, quién y cuándo
  la asignó, salida habitual de la clínica, aviso si falta) y sección «Bultos»
  (total, identificador, palet, salida del palet) con el botón «Registrar bulto
  de este pedido», que abre Admin → Bultos con el pedido ya elegido.
- **Bultos** y **Palets**: muestran la salida del pedido y la del palet.

## Odoo

VetStock crea (si no existen) dos campos más en `sale.order`, en **solo lectura**:

| Campo | Contenido |
|---|---|
| `x_vetstock_bultos` (entero) | Número de bultos registrados en VetStock |
| `x_vetstock_salida` (texto) | Salida, p. ej. «14 - Regional» |

Además añade las columnas a la lista «VetStock» (menús *Pagados pendientes de
firma* y *Firmados y pagados*) y una vista heredada del formulario de pedido
(`vetstock.sale.order.form.logistica`) que los enseña debajo de las condiciones de pago.
La fuente de verdad es VetStock: en Odoo no se pueden editar.

Se sincroniza (después del commit, con el mismo `client_order_ref = VSM-{id}`, sin
duplicar) al asignar salida, registrar un bulto, preparar y expedir. Solo se
exportan pedidos pagados, como antes.

**Si Odoo no responde**, el cambio se guarda en VetStock igualmente, el pedido
queda «pendiente de sincronizar» con el error visible en su detalle, y se
reintenta con «Actualizar en Odoo» (o `POST /api/odoo/pedidos/{id}/exportar`).

### Cómo verlo en Odoo

1. Entra en http://localhost:8069 (cuenta de la demo).
2. Ventas → Pedidos → **Firmados y pagados (VetStock)**: columnas *Bultos* y *Salida*.
3. Abre un pedido (por ejemplo `VSM-17`): bajo *Payment Terms* aparecen *Bultos* y
   *Salida*, sin poder editarlos.

No hace falta instalar nada: los campos y las vistas se crean la primera vez que se
exporta un pedido.

## Pedidos antiguos

La migración **no asigna salida a ningún pedido existente**. En la base de la demo
no había pedidos preparados ni expedidos cuando se aplicó, así que no hubo nada que
completar. Si en otra base los hubiera, se pueden listar con el filtro
«Salida: Sin asignar» y decidir uno a uno; no se ha hecho ningún cambio masivo.

## La pantalla de bultos vacía

No era un error: la API devolvía `200 []` porque no había bultos ni palets (eran
de pedidos de prueba borrados en la limpieza anterior). No se añadieron bultos
ficticios para «llenarla»; los que hay ahora son los de las pruebas descritas en
[pruebas.md](pruebas.md).

## Capturas

En [capturas/logistica](capturas/logistica/): salidas con código, salida habitual,
aviso de pedido sin salida, pedido preparado con salida, bultos por pedido,
sincronización pendiente con Odoo parado y columna «Salida» en pedidos. El panel de
navegador usado no pintaba Odoo, así que Odoo se comprobó leyendo la página y por
JSON-RPC (ver pruebas).
