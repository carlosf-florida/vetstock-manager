# Guion para defender el proyecto (≈10 minutos)

## Antes de clase

1. Abre Docker Desktop y arranca la aplicación con `docker compose up -d` desde `vetstock-manager`.
   Comprueba que abren http://localhost:5173 y, si vas a enseñar Odoo, http://localhost:8069.
2. **No reinicies los datos si vas a enseñar Odoo.** El reinicio borra los pedidos de VetStock y
   sus números vuelven a empezar, de modo que podrían coincidir con los que ya están en Odoo
   (`VSM-{número}`). La demo crea un pedido nuevo, así que no hace falta reiniciar.
3. Abre dos navegadores (o una ventana normal y otra privada) para tener dos sesiones a la vez.
4. Ten a mano las cuentas de demostración (`database/README.md`): `aux.sanroque`, `dr.garcia`,
   `dr.martin` y `admin`.

Los pedidos enviados a firma sin pago caducan a las 72 h, así que los pedidos antiguos pueden
aparecer cancelados: es la regla funcionando, no un error.

## 1. Problema y solución (1 min)

«Una distribuidora veterinaria recibe pedidos de clínicas que necesitan la
**autorización de un veterinario** y el **pago** antes de prepararse. VetStock
Manager une el portal de las clínicas y la gestión del almacén, con una regla
central: *ningún pedido se prepara sin firma válida del veterinario asignado y pago completo*.»

Tecnologías: Spring Boot 4 (Java 21), MySQL, React + Vite, pruebas JUnit y Docker; Odoo 18 es una
integración opcional.

## 2. Recorrido principal en directo (5 min)

1. **`aux.sanroque`**: catálogo → carrito con **3 sacos de pienso y 1 caja de guantes** (el IVA y el total los calcula el servidor) → *Enviar a firma*. Mostrar que el stock queda **reservado** y que la auxiliar no puede firmar.
2. **`dr.garcia`** (otra ventana): el pedido → *Revisar y firmar* → ve el contenido → dibuja la firma → confirma. Ahora está **Firmado** y bloqueado.
3. *Pagar* → pasarela de **demostración** → *Simular pago fallido* → *Reintentar* (referencia nueva) → *Simular pago correcto* → **Listo para preparar**.
   Explicar: «El pedido no se marca como pagado por volver a la página de éxito, sino cuando el backend recibe una notificación **firmada con HMAC** y comprueba el importe y la moneda.»
4. **`admin`**: Pedidos (columnas de firma, pago, salida, peso y bultos) → abrir el pedido → *Iniciar preparación* → sección «Peso y bultos»: **38,1 kg → 2 bultos** → *Marcar como preparado*: se crean solos `VSM-{pedido}-B1` y `B2`, y toma la salida habitual **14 - Regional**.
5. **Bultos**: asignar los dos bultos a un palet de la salida 14 - Regional (si hace falta, crearlo en Palets) → *Expedir* → en Stock se ve que el físico baja una sola vez.

## 2b. Firma y pago separados (1 min)

Admin → Pedidos: «Pagados pendientes de firma», «Firmados y pagados» y «Otros pendientes».
Explicar: «Se puede pagar antes o después de firmar, pero pagado no basta: hace falta la firma
VÁLIDA del veterinario asignado sobre el contenido actual. Lo decide una regla fija en el backend
(`AutorizacionPedido`), no una IA.»
En Administración, la pestaña «Por salida» separa los pedidos por salida y permite imprimir el listado para comprobarlos en el almacén.
En Odoo → Sales → Orders aparecen las mismas dos listas, con *Bultos*, *Salida* y *Peso (kg)*, y un apartado más, «Pedidos por salida»;
cuando un pedido está pagado y con firma válida, Odoo lo pasa solo de presupuesto a pedido de venta.

## 3. Seguridad y reglas (2 min)

- Los permisos están en el **backend**: entrar como `dr.martin` (otra clínica) y abrir `/pedidos/1`, que es de San Roque → acceso denegado.
- Administración puede crear pedidos para una clínica (queda «Creado por»), pero **no firma ni paga**.
- Concurrencia: la reserva es una sola sentencia SQL condicionada. Hay una prueba con dos hilos que se disputan las últimas unidades y solo uno lo consigue.
- Las reservas sin pago caducan a las 72 h.
- La salida, el peso y los bultos son logística interna: la API solo los devuelve a administración.

## 4. Calidad (1 min)

- `mvnw.cmd test` → **90 pruebas**, incluidas las de integración contra MySQL real que recorren todo el flujo y los límites de los bultos (25 kg → 1; 25,01 kg → 2).
- Las pruebas encontraron errores reales, ya corregidos: el script SQL no se podía repetir, editar líneas fallaba y editar un producto desde su pantalla daba error. Revisar los registros de la aplicación descubrió otro: con dos o más reservas vencidas, la caducidad fallaba; ahora hay una prueba que lo cubre.

## 5. Límites honestos (1 min)

- La pasarela es **simulada** (no cobra). Para producción haría falta Redsys/Bizum con contrato y backend público.
- La firma es **simple**, sin validez legal certificada.
- Los documentos son de prueba, no facturas fiscales.
- Los **pesos de los productos son ficticios**, inventados para la demo.
- CSRF está desactivado por simplicidad; en producción habría que activarlo.
- No hay edición ni baja de usuarios, y un palet no se marca como expedido desde la pantalla.
- Power BI: las vistas están preparadas; los gráficos de la aplicación son propios (React).

## Qué es automático y qué es manual

| Automático (reglas fijas del backend) | Manual (lo confirma una persona) |
|---|---|
| Pasar a «Listo para preparar» con firma válida y pago completo | Crear el pedido y enviarlo a firma |
| Calcular precios, IVA, peso y bultos | Firmar o rechazar (veterinario asignado) |
| Crear al preparar los bultos que falten | Pagar (clínica) |
| Asignar la salida habitual al preparar | Iniciar preparación y marcar como preparado |
| Sincronizar con Odoo y confirmar allí el pedido | Meter los bultos en un palet y expedir |
| Descontar el stock al expedir y caducar reservas | Reintentar si Odoo estaba caído |

Resumen: «Lo que se puede comprobar con datos es automático; lo que es trabajo físico del almacén
lo confirma una persona, y el sistema solo lo permite si se cumplen las reglas.»

## Preguntas probables

**Firma y pago**

- *¿Se puede pagar antes de firmar?* → Sí. Queda en «Pagados pendientes de firma» y no se puede preparar hasta tener la firma válida.
- *¿Y si el veterinario rechaza un pedido ya pagado?* → Se cancela, se libera el stock y queda «devolución pendiente». La devolución se hace fuera de la aplicación; los reembolsos están fuera del alcance.
- *¿Qué es una firma válida?* → La del veterinario asignado, y que la copia guardada coincida con las líneas y el total actuales.
- *¿Qué pasa si llegan dos notificaciones de pago a la vez?* → El pago se bloquea con `SELECT … FOR UPDATE`. La segunda ve el pago ya resuelto y no repite efectos.
- *¿Y si alguien cambia el importe en el navegador?* → El servidor ignora los precios del navegador, y la notificación debe coincidir con el total calculado.

**Bultos y logística**

- *¿Cómo se calculan los bultos?* → Peso total (peso por unidad × cantidad) entre 25 kg, redondeado hacia arriba, mínimo 1. Con `BigDecimal`, para evitar errores en los límites.
- *¿Y si un producto no tiene peso?* → El cálculo queda incompleto y no se puede preparar. Un peso desconocido nunca cuenta como 0.
- *¿Cuál es la diferencia entre bultos calculados y registrados?* → Los calculados son un número que sale del peso; los registrados son las filas de la tabla `bultos`, con identificador y palet. Para expedir deben coincidir.
- *¿Y si sobran bultos?* → No se borra nada solo: administración saca el bulto del palet y elimina el sobrante, salvo que el pedido o el palet ya estén expedidos.
- *¿Por qué un bulto no puede ir en cualquier palet?* → Porque el palet va a una salida y debe ser la misma que la del pedido.
- *¿Se puede descontar el stock dos veces?* → No: solo se descuenta al pasar a EXPEDIDO, y repetir esa transición se rechaza.

**Odoo y arquitectura**

- *¿Qué pasa si Odoo está caído?* → El cambio se guarda en VetStock, el pedido queda «pendiente de sincronizar» con el error visible y se reintenta con «Actualizar en Odoo». No se duplican pedidos porque la referencia es siempre `VSM-{id}`.
- *¿Se puede editar en Odoo?* → Los campos de VetStock son de solo lectura: la fuente de los datos es VetStock.
- *¿Hay un agente de IA?* → No. Son reglas fijas y deterministas.
- *¿Cómo está organizado el backend?* → En capas: controladores, servicios y repositorios, con adaptadores para pagos y Odoo (`guia-del-proyecto.md`).
