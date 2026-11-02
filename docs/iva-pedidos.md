# IVA configurado en VetStock Manager

## Regla del proyecto

A petición del usuario, los **pedidos nuevos** aplican un IVA de prueba del **4%**, añadido a precios de producto que son netos. No se presenta como un tipo fiscal legal aplicable a todo el catálogo veterinario; antes de usar el sistema en producción hay que configurar los impuestos reales por producto y jurisdicción.

El backend conserva `pedidos.iva_porcentaje` al crear cada pedido. No se permite enviarlo desde el navegador ni cambiarlo por API. Usa `BigDecimal`, calcula base por línea, redondea el IVA de cada línea a dos decimales con `HALF_UP` y suma los impuestos redondeados. El total a cobrar es base más IVA, en EUR.

Ejemplo: dos unidades a 14,50 EUR netos producen base 29,00 EUR, IVA 1,16 EUR y total 30,16 EUR.

## Dónde se muestra

El catálogo identifica los precios como netos. El carrito muestra un desglose estimado; el importe definitivo lo calcula el backend. La ficha del pedido, administración, Pago y la pasarela muestran base, porcentaje, IVA y total. El pago simulado y su notificación validan el total **con IVA**, nunca una cifra elegida por el cliente. La copia inmutable de una firma nueva incluye el desglose y la moneda.

## Odoo

El adaptador configura un impuesto propio de ventas del 4%, excluido del precio, para la empresa del usuario de API; no modifica tipos fiscales ajenos ni impuestos de compras. En las líneas de pedidos nuevos reemplaza el impuesto predeterminado por el impuesto propio: no acumula 4% y 15%.

Los pedidos usan una lista de precios propia en EUR. Se activa EUR si hacía falta, pero no se cambia la moneda global de la empresa ni se revalúa su contabilidad. El adaptador compara base, IVA, total y moneda devueltos por Odoo con el pedido de VetStock. Una discrepancia queda registrada como fallo de sincronización; no se dice que un pedido se exportó correctamente con otro importe.

El impuesto propio se vincula a `account.tax.group` llamado **IVA 4% (VetStock)**, limitado a la misma empresa y país fiscal. Esto evita heredar la etiqueta predeterminada «Tax 15%» en el resumen. Para un impuesto VetStock 4% ya existente, se valida primero su definición y se cambia únicamente `tax_group_id`; no se renombra el grupo compartido del 15%, ni se alteran tipos, importes, pagos o estados de pedidos. Al sincronizar productos con el backend actualizado se aplica también a los resúmenes que usan ese impuesto propio.

El redondeo esperado en Odoo es `round_per_line`. Una configuración incompatible debe corregirse expresamente, no cambiarse silenciosamente para todos los clientes.

## Pedidos anteriores

La migración **005_iva_pedidos.sql** añade la columna inicialmente con 0% para pedidos existentes; después establece 4% como valor por defecto para los siguientes. Repetirla no cambia tasas ya guardadas. No borra datos, modifica pagos ni altera firmas anteriores.

Por ejemplo, el pedido #9 ya pagado por 29,00 EUR conserva ese importe. No se le añade IVA retroactivo ni se vuelve a cobrar. Las líneas, impuestos y moneda de un pedido ya creado en Odoo no se modifican al actualizar la firma: solo se actualizan sus notas. Si un histórico de Odoo tiene el antiguo impuesto del 15% o moneda USD y no coincide con VetStock, se registra la discrepancia para revisión, no se sobrescribe automáticamente.

Para comprobar el nuevo IVA hay que crear un pedido nuevo, pagar el importe con IVA y revisar su presupuesto de Odoo.

## Actualizar

Aplicar únicamente la migración 005 sobre una instalación que ya tiene la migración 004. No ejecutar el esquema inicial, que borra tablas. Reconstruir backend y frontend sin eliminar volúmenes. La configuración propia de Odoo se prepara al sincronizar productos o exportar el pedido; no es necesario reinstalarlo.
