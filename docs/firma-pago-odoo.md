# Firma interna, pago y sincronización con Odoo

## Qué cambia

El pedido conserva el veterinario responsable asignado automáticamente a la clínica. Solo ese veterinario puede firmar el pedido, con su propia cuenta. Un auxiliar lo deja pendiente de firma. Se guarda la cuenta que firma, su rol, la fecha y una copia inmutable del pedido.

Se puede dibujar con ratón/táctil o subir una imagen PNG/JPG de hasta 5 MB. El número de colegiado es opcional y solo identifica al responsable. Si se introduce, debe coincidir con el responsable asignado; no es una contraseña, un factor de autenticación ni un método de firma. No se admite confirmar escribiendo únicamente ese número.

Este proyecto registra una confirmación comercial interna y una firma electrónica simple de demostración. No implementa una firma cualificada, certificada ni una autorización o prescripción veterinaria verificable.

## Firma y pago independientes

| Situación | Firma | Pago | Estado operativo |
|---|---|---|---|
| Pedido confirmado, aún sin firmar ni pagar | Pendiente | Pendiente | PENDIENTE_FIRMA |
| Pagado, se firmará después | Pendiente | Pagado | PENDIENTE_FIRMA |
| Confirmado con firma, aún sin pagar | Firmado | Pendiente | FIRMADO |
| Firmado y pagado | Firmado | Pagado | LISTO_PREPARAR |

El estado de pago procede exclusivamente de la notificación validada del proveedor simulado. Cambiar la pantalla o marcar una casilla no acredita un pago. El importe lo calcula el backend.

Los usuarios de la clínica ven sus pedidos en **Mis pedidos**, con indicadores separados y un filtro de firmas pendientes. No ven pedidos de otras clínicas. Solo el veterinario asignado puede abrir la acción de firma. Administración no puede firmar en nombre de ellos.

La reserva de stock se realiza al confirmar el pedido. El almacén no puede iniciar preparación ni expedir hasta tener pago y confirmación interna registrados. El stock físico se descuenta una sola vez al expedir.

## Odoo

Un pago confirmado solicita automáticamente exportar el pedido a Odoo después de confirmar la transacción local. La firma posterior solicita actualizar el mismo pedido. Se mantiene `client_order_ref = VSM-{id}` y el ID de `sale.order`.

El estado de firma se muestra en un bloque de notas del pedido de Odoo, junto al responsable y al firmante real. No se cambia ni se confunde con el campo comercial `state` de Odoo. El pedido se crea como documento de venta según la configuración estándar de Odoo; exportarlo no registra un cobro bancario, factura ni valida una receta.

El bloque gestionado por VetStock se sustituye en cada actualización; las demás notas manuales permanecen. Si el ERP no está disponible, el pago y la firma ya confirmados no se deshacen. El pedido conserva `odoo_sincronizacion_pendiente = true` y, cuando se puede guardar, el detalle del fallo. Administración puede reintentar desde la ficha. No hay aún un trabajador de reintentos periódico ni sincronización bidireccional.

## Actualización de una instalación existente

Ejecutar una vez (se puede repetir sin borrar datos):

```text
database/migrations/004_firma_pago_independientes.sql
```

Añade `firmas.numero_colegiado`, `pedidos.odoo_sincronizacion_pendiente` y `pedidos.odoo_ultimo_error`. No modifica las firmas ni pagos históricos. El esquema inicial se ha actualizado para instalaciones nuevas; **no se debe volver a ejecutar el esquema inicial en una instalación con datos**, pues contiene borrados de tablas.

Después hay que reconstruir backend y frontend, sin eliminar volúmenes de Docker. No es necesario reinstalar Odoo ni sus aplicaciones.
