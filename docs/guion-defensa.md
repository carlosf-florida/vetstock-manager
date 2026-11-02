# Guion para defender el proyecto (≈10 minutos)

**Antes de clase:** reinicia los datos de la demo (`instalacion-windows.md`,
apartado 4), arranca la aplicación y abre dos navegadores (o una ventana
normal y otra privada) para tener dos sesiones a la vez.

## 1. Problema y solución (1 min)

«Una distribuidora veterinaria recibe pedidos de clínicas que necesitan la
**autorización de un veterinario** y el **pago** antes de prepararse. VetStock
Manager une el portal de las clínicas y la gestión del almacén, con una regla
central: *ningún pedido se prepara sin firma del veterinario y pago confirmado*.»

Tecnologías: Spring Boot 4 (Java 21), MySQL, React + Vite y pruebas JUnit.

## 2. Recorrido principal en directo (5 min)

1. **`aux.sanroque`**: catálogo → carrito (el IVA y el total los calcula el servidor) → *Enviar a firma*. Mostrar que el stock queda **reservado** y que no puede firmar ni pagar.
2. **`dr.garcia`** (otra ventana): el pedido → *Revisar y firmar* → ve el contenido → dibuja la firma → confirma. Ahora está **Firmado** y bloqueado.
3. *Pagar* → pasarela de **demostración** → *Simular pago fallido* → *Reintentar* (referencia nueva) → *Simular pago correcto* → **Listo para preparar**.
   Explicar: «El pedido no se marca como pagado por volver a la página de éxito, sino cuando el backend recibe una notificación **firmada con HMAC** y comprueba el importe y la moneda.»
4. **`admin`**: Pedidos (filtros y columnas de firma y pago) → *Iniciar preparación* → intentar *Marcar como preparado* sin bultos (regla) → Bultos → Palets (destino Valencia) → asignar → *Preparado* → *Expedir* → en Stock se ve que el físico baja una sola vez.

## 2b. Firma y pago separados (1 min)

Admin → Pedidos: «Pagados pendientes de firma», «Firmados y pagados» y «Otros pendientes».
Explicar: «Pagado no basta: hace falta la firma VÁLIDA del veterinario asignado sobre el
contenido actual. Lo decide una regla fija en el backend (`AutorizacionPedido`), no una IA.»
En Odoo → Sales → Orders aparecen las mismas dos listas como entradas de menú.

## 3. Seguridad y reglas (2 min)

- Los permisos están en el **backend**: entrar como `aux.mediterraneo` y abrir `/pedidos/2` → acceso denegado.
- Administración puede crear pedidos para una clínica (queda «Creado por»), pero **no firma ni paga**.
- Concurrencia: la reserva es una sola sentencia SQL condicionada. Hay una prueba con dos hilos que se disputan las últimas unidades y solo uno lo consigue.
- Las reservas sin pago caducan a las 72 h.

## 4. Calidad (1 min)

- `mvnw.cmd test` → **60 pruebas**, incluidas las de integración contra MySQL real que recorren todo el flujo.
- Las pruebas encontraron errores reales (el script SQL no se podía repetir, editar líneas fallaba), y están corregidos.

## 5. Límites honestos (1 min)

- La pasarela es **simulada** (no cobra). Para producción haría falta Redsys/Bizum con contrato y backend público.
- La firma es **simple**, sin validez legal certificada.
- Los documentos son de prueba, no facturas fiscales.
- Power BI: las vistas están preparadas; los gráficos de la aplicación son propios (React).

## Preguntas probables

- *¿Qué pasa si llegan dos notificaciones de pago a la vez?* → El pago se bloquea con `SELECT … FOR UPDATE`. La segunda ve el pago ya resuelto y no repite efectos.
- *¿Y si alguien cambia el importe en el navegador?* → El servidor ignora los precios del navegador, y la notificación debe coincidir con el total calculado.
- *¿Por qué no se puede pagar antes de firmar?* → Para no cobrar pedidos que el veterinario podría no autorizar, ya que los pagados no se cancelan.
