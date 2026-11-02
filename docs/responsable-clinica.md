# Veterinario responsable por clínica

Cada clínica tiene un veterinario responsable configurable en **Administración → Clínicas**, mediante `PUT /api/clinicas/{id}/responsable` `{veterinarioId}` (solo ADMIN). Debe pertenecer a la misma clínica. La migración 002 añade la relación y asigna inicialmente el primer veterinario si existe.

Los pedidos del cliente usan automáticamente ese responsable, sin elegirlo en el carrito. Una clínica sin responsable no puede crear pedidos. El responsable aparece en VetStock y en las notas del contacto empresa (`res.partner`) de Odoo.

## Responsable y firmante son distintos

La confirmación interna puede registrarla el responsable o un auxiliar autenticado de la clínica con su propia firma. Se guarda quién confirmó realmente, su rol y la fecha. Nunca se atribuye al veterinario una firma del auxiliar. El nº de colegiado es opcional e identificativo, no un método de autenticación. La confirmación comercial del prototipo no valida una prescripción ni una firma certificada.

## Pagar y firmar en cualquier orden

Se puede dibujar la firma, subir PNG/JPG o elegir **Firmar más tarde y pagar**. Un pedido pagado sin firma permanece `PENDIENTE_FIRMA` y se envía automáticamente a Odoo como **Por firmar**. Al firmarlo después desde **Mis pedidos**, se actualiza el mismo pedido de Odoo. El almacén solo puede prepararlo cuando firma y pago estén completados.

Detalle del flujo y actualización: [firma-pago-odoo.md](firma-pago-odoo.md).

## Sincronización de contactos

Se actualiza el contacto por `clinicas.odoo_id`, aunque su nombre estuviera mal codificado. No se crea un segundo contacto por corregir un acento. Se conservan las demás notas manuales. Si el ID vinculado no existe, falla de forma explícita para revisar el vínculo. Sin ID, se buscan nombre y variantes antiguas; ante varias coincidencias no se elige arbitrariamente.

Los productos se actualizan por `default_code`, sin alterar impuestos ni tarifas existentes. JSON-RPC utiliza UTF-8. Los pedidos pagados utilizan `client_order_ref = VSM-{id}` para identificarlos y permitir reintentos sin duplicar.
