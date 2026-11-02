# Firma del veterinario y pagos: qué es real y qué es simulado

| Pieza | Situación |
|---|---|
| Firma (dibujo o imagen), identidad del firmante y copia inmutable | **Real** en la aplicación. Es una firma electrónica simple **sin validez legal certificada** |
| Reglas de pago (solo tras firma, importe del servidor, idempotencia, reintentos) | **Reales** en el backend y probadas |
| Webhook autenticado con HMAC-SHA256 | **Real** (mismo mecanismo que usaría un proveedor) |
| Proveedor de pago, cobro y Bizum | **Simulado**: pasarela de demostración. **No se cobra nada** ni hay integración bancaria |
| Sandbox de un proveedor real | **No configurado**: no hay contrato ni credenciales (decisión acordada para la entrega) |

## Firma

1. El auxiliar (o el propio veterinario) envía el pedido a firma. El stock queda reservado.
2. Solo el **veterinario asignado** ve la acción «Revisar y firmar». El backend lo
   comprueba también: un auxiliar, otro veterinario o administración reciben `403`.
3. La pantalla muestra las líneas, precios e importes que se autorizan. Hay que
   dibujar la firma (ratón o dedo; el lienzo tiene `touch-action: none`) o subir
   un PNG/JPG de hasta 5 MB, y marcar «He revisado… y autorizo».
4. El backend valida el tipo real del archivo por sus bytes (PNG/JPG), el tamaño
   y la confirmación. Guarda la firma con el firmante, la fecha y una **copia JSON**
   del contenido (líneas, precios, IVA, total). No reutiliza firmas anteriores.
5. El pedido pasa a **FIRMADO (pendiente de pago)** y su contenido ya no se puede modificar.

El nº de colegiado es opcional y solo identifica; no es una contraseña. La
imagen de la firma solo la pueden ver la clínica del pedido y administración
(`GET /api/pedidos/{id}/firma/imagen`, con comprobación de permisos). Se guarda
en la base de datos, no en una carpeta pública.

## Pago (adaptador de demostración)

```
Clínica: POST /api/pagos/{pedido}/iniciar  ──►  referencia SIM-…, importe calculado en el servidor
Pasarela simulada (con sesión de la clínica): el usuario elige «correcto» o «fallido»
   └─► el "proveedor" envía POST /api/pagos/notificacion
         cuerpo JSON {referencia, importe, moneda, resultado}
         cabecera X-Firma-Notificacion = Base64(HMAC-SHA256(secreto, cuerpo))
Backend: verifica la firma HMAC (comparación en tiempo constante)
         bloquea el pago (SELECT … FOR UPDATE)
         si ya estaba resuelto → no hace nada (idempotente)
         comprueba importe == total del pedido y moneda == EUR
         CONFIRMADO → pedido LISTO_PREPARAR · FALLADO → se puede reintentar
```

- Volver a una «página de éxito» **no** marca nada: el estado sale solo de la notificación.
- Importe o moneda incorrectos dejan el intento FALLADO y responden con `400`.
- Firma HMAC ausente o incorrecta: `403`, sin tocar el pago.
- Reintento: un intento FALLADO no se reutiliza; se crea otro con referencia
  nueva. Si hay uno PENDIENTE, se devuelve ese mismo, así no se duplican cobros.
- Pagos abandonados: tras 60 min en PENDIENTE pasan a FALLADO.
- Administración no puede iniciar ni completar pagos, ni marcarlos a mano.
- No se guardan datos de tarjeta ni bancarios: la pasarela de demo no los pide.
- `PAGOS_SIMULADO_ACTIVO=false` desactiva la pasarela simulada.

## Qué haría falta para cobrar de verdad (producción)

Opción natural en España: **Redsys** (TPV virtual del banco), que admite tarjeta y,
si el banco lo habilita, **Bizum**.

1. Contrato de TPV virtual con un banco y alta de Bizum en ese comercio (puede tener costes).
2. Credenciales del entorno de pruebas del banco (número de comercio, terminal y clave de firma).
3. Sustituir solo el adaptador: generar la petición firmada (`Ds_MerchantParameters` +
   `Ds_Signature`) en `iniciar()`, y en el webhook verificar la firma de Redsys
   (HMAC-SHA256 con clave derivada 3DES por pedido) en lugar de la de demo. El resto
   de reglas (importe, moneda, idempotencia, estados) ya está implementado.
4. Un backend accesible por HTTPS desde Internet para recibir la notificación (en
   local haría falta un túnel). **No se ha hecho despliegue público.**
5. Desactivar la pasarela simulada y guardar la clave en una variable de entorno o gestor de secretos.
6. **Transferencia:** solo tendría sentido con un proveedor que confirme automáticamente
   la recepción (por ejemplo, transferencia inmediata con notificación). No se ha
   implementado porque no se revisan justificantes a mano.

## Odoo (opcional, de fases anteriores)

Un pago confirmado solicita exportar el pedido a Odoo después del commit
(adaptador `ODOO_MODO=REAL`; por defecto `SIMULADO`). Si Odoo falla, el pago no se
deshace y el pedido queda marcado para reintentar desde administración. Exportar a
Odoo no registra cobros ni facturas fiscales.
