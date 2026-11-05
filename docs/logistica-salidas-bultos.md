# Logística: salidas de expedición y bultos por pedido

Actualizado el 3 de noviembre de 2026 (rama `feature/logistica-salidas-bultos`).

## Qué problema resuelve

Antes, el pedido no sabía por qué **salida de expedición** tenía que salir y los
bultos solo se veían en la pantalla Admin → Bultos. Ahora:

- Cada clínica puede tener una **salida habitual**. Ejemplo confirmado: la
  Clínica Veterinaria San Roque sale por la salida **14 - Regional**.
- Al marcar un pedido como **PREPARADO** se le asigna esa salida (salvo que
  administración ya le haya elegido otra a mano).
- El listado y el detalle del pedido muestran su **peso**, los **bultos que le corresponden**
  (hasta 25 kg por bulto) y por qué salida va.
- Odoo recibe los **Bultos**, el **Peso** y la **Salida** del pedido, en solo lectura.

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
nombre si no hay código. El **número de bultos** no se guarda en el pedido: se calcula a
partir del peso (ver «Bultos calculados por peso»).

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
- **Pedidos**: columnas «Salida» («Sin asignar» si no tiene), «Peso» y «Bultos» (calculados; si
  no coinciden con los registros se indica cuántos hay), y filtro por salida.
- **Productos**: campo «Peso por unidad (kg)» y columna «Peso/ud.» («Sin peso» si falta).
- **Detalle del pedido**: tarjeta «Salida de expedición» (salida, quién y cuándo
  la asignó, salida habitual de la clínica, aviso si falta) y sección «Peso y bultos»
  (peso total, bultos calculados, aviso si faltan pesos, registros con su palet, avisos si
  faltan o sobran, «Sacar del palet» y «Eliminar sobrante»). «Registrar bulto de este pedido» solo aparece si
  faltan registros.
- **Bultos** y **Palets**: muestran la salida del pedido y la del palet.

## Bultos calculados por peso (máx. 25 kg por bulto)

**Regla:** cada pedido lleva al menos 1 bulto y cada bulto admite hasta 25 kg.

```
peso total = suma de (peso por unidad × cantidad) de cada línea
bultos     = peso total / 25 redondeado HACIA ARRIBA, mínimo 1
```

| Peso | Bultos |
|---|---|
| 10 kg · 24,99 kg · 25 kg exactos | 1 |
| 25,01 kg · 49,99 kg · 50 kg exactos | 2 |
| 50,01 kg · 75 kg exactos | 3 |

- **Dónde está el peso:** `productos.peso_unitario_kg` (migración `011`), en kg por unidad
  de venta con 3 decimales (gramos exactos), embalaje incluido. Solo se admite kg. Se edita en
  Admin → Productos. Vacío = **desconocido**, nunca 0.
- **Pesos de la demo: FICTICIOS.** Ningún producto tenía peso. Los valores de
  `database/demo/pesos_ficticios_demo.sql` (y del esquema `001`) son estimaciones inventadas
  para poder probar; no son pesos medidos. El nombre «Pienso 12 kg» o «Gel 1 L» indica el
  contenido, no el peso de envío, y los ml no son gramos: no se usaron como pesos.
- **Cálculo en el backend** (`PesoPedido`, regla fija como `AutorizacionPedido`), con
  `BigDecimal`: 25,000 kg da 1 bulto y 25,001 kg da 2, sin errores de coma flotante. El
  frontend solo muestra el resultado. Se calcula al leer el pedido, así que siempre
  corresponde a sus líneas actuales (que solo cambian en BORRADOR).
- **Si falta algún peso:** el cálculo queda *incompleto*: se muestra el peso conocido como
  mínimo («≥ 18,5 kg»), qué productos faltan, y los bultos quedan **sin calcular**. No se
  pueden registrar bultos ni marcar el pedido como preparado.

### Bultos calculados y registros de bultos

Son dos cosas distintas y no se dejan contradictorias:

- **Bultos calculados**: los que exige el peso (un número).
- **Registros de bultos** (tabla `bultos`): cada paquete físico, con identificador único,
  pedido, palet y salida.

| Momento | Qué pasa |
|---|---|
| Registro manual (Admin → Bultos) | Permitido solo hasta el número calculado |
| **Marcar como preparado** | Exige peso completo. Crea solos los registros que falten con identificador `VSM-{pedido}-B{n}` (p. ej. `VSM-21-B1`). Repetir no crea duplicados |
| Hay MÁS registros que los calculados | No se prepara ni se expide, y **no se borra nada solo**. Admin puede eliminar un sobrante si no está en un palet y el pedido no está expedido (`DELETE /api/bultos/{id}`). Si está en un palet, primero «Sacar del palet» |
| Sacar del palet | Ficha del pedido o Admin → Bultos (`PATCH /api/bultos/{id}/quitar-palet`). No si el palet o el pedido están expedidos, ni en un pedido cancelado |
| Expedir | Exige peso completo, registros = calculados y todos en un palet |

El cálculo dice **cuántos** bultos exige la regla; no reparte el peso entre ellos ni dice qué
lleva cada uno.

### En Odoo

`x_vetstock_bultos` = bultos **calculados** (vacío si faltan pesos) y el nuevo campo
`x_vetstock_peso_kg` («Peso total (kg)», 3 decimales, solo lectura) en la ficha del pedido y en
la lista VetStock. Odoo no mantiene ningún contador propio.

### Salida prevista

Desde que se realiza el pedido se ve la salida habitual de su clínica como **prevista**, en VetStock y en Odoo. No se guarda en el pedido: la
definitiva se fija al prepararlo o al elegirla administración. Si cambia la habitual de la clínica, lo previsto cambia, y Odoo lo recibe en la siguiente sincronización del pedido.

## Odoo

VetStock crea (si no existen) dos campos más en `sale.order`, en **solo lectura**:

| Campo | Contenido |
|---|---|
| `x_vetstock_bultos` (entero) | Bultos calculados por peso (vacío si faltan pesos) |
| `x_vetstock_peso_kg` (decimal) | Peso total del pedido en kg (vacío si faltan pesos) |
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
2. Ventas → Pedidos → **Firmados y pagados (VetStock)**: columnas *Bultos*, *Salida* y *Peso (kg)*.
3. Abre un pedido (por ejemplo `VSM-21`): bajo *Payment Terms* aparecen *Bultos*,
   *Peso total (kg)* y *Salida*, sin poder editarlos.

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
