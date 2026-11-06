# Pruebas

## 1. Pruebas automáticas

```powershell
cd backend
.\mvnw.cmd test
```

- **Unitarias** (Mockito, sin base de datos): reglas de firma, pago, IVA, caducidad
  y adaptadores de Odoo.
- **Integración** (`backend/src/test/java/es/florida/vet/integracion/`): arrancan
  la aplicación completa (seguridad, controladores, servicios) y hacen peticiones
  HTTP con MockMvc contra **MySQL real**. Usan la base `vetstock_test`, que se crea
  y recarga con `001_esquema_inicial.sql` + `008_vistas_powerbi.sql` (así se prueba
  también el esquema real). Si MySQL no está disponible, se **omiten**.

Variables opcionales para la base de pruebas: `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`.

### Última ejecución registrada

- Fecha: 5 de noviembre de 2026 · Windows 11 · Temurin JDK 21.0.8 · MySQL 8.4 (Docker).
- Resultado: **112 pruebas, 0 fallos, 0 errores, 0 omitidas** (6 de noviembre de 2026).
- La prueba de concurrencia se repitió 3 veces seguidas con el mismo resultado.

### Correspondencia con las pruebas pedidas

| Caso | Prueba automática |
|---|---|
| Inicio y cierre de sesión | `PermisosIntegracionTest.loginYLogout` |
| Separación de permisos entre clínicas | `PermisosIntegracionTest.separacionEntreClinicas` |
| Cliente que crea un pedido | `RecorridoPedidoIntegracionTest.recorridoCompleto` (pasos 1-2) |
| Administración ayuda y registra la autoría | `…administracionAyudaAlCliente` |
| Auxiliar que no puede firmar | `recorridoCompleto` (paso 3) y `PedidoFirmaPagoTest.auxiliarNoPuedeFirmar` |
| Veterinario que firma su pedido / uno asignado | `…veterinarioFirmaSuPropioPedido` y `recorridoCompleto` (paso 4) |
| Validación de archivos de firma | `…validacionArchivoFirma` |
| Bloqueo de cambios tras firmar | `recorridoCompleto` (paso 4) |
| Bloqueo de preparación sin firma | `recorridoCompleto` (paso 3) y `PedidoFirmaPagoTest` |
| Bloqueo de preparación sin pago | `recorridoCompleto` (paso 4) |
| Pago fallido y reintento | `recorridoCompleto` (pasos 5-6) |
| Notificación de pago duplicada | `recorridoCompleto` (paso 9) y `notificacionRepetidaEsIdempotente` |
| Importe o moneda incorrectos | `recorridoCompleto` (pasos 7-8) y `importeOMonedaIncorrectosMarcanFallado` |
| Firma HMAC falsa o ausente | `recorridoCompleto` (paso 7) y `notificacionConFirmaIncorrectaSeRechaza` |
| Stock insuficiente y reservas concurrentes | `…stockInsuficienteYReservasConcurrentes` (dos hilos a la vez) |
| Acceso no autorizado a documentos | `PermisosIntegracionTest.documentosConPermisos` |
| Bultos, palets y expedición | `recorridoCompleto` (pasos 10-11) |
| Recorrido completo de creación a expedición | `recorridoCompleto` |
| Caducidad de reservas | `…caducidadDeReservas` y `caducidadCancelaYLiberaReserva` |
| Pagado sin firma → «pagados pendientes de firma», no se prepara; al firmar pasa a «firmados y pagados» sin duplicarse | `ClasificacionFirmaPagoIntegracionTest.pagadoSinFirmaYDespuesFirmado` |
| Firmado sin pago → no está listo | `…firmadoSinPago` |
| Pago parcial → no cuenta como pagado | `…pagoParcialNoCuenta` |
| En preparación → «firmado y pagado» pero no «listo» | `…pagadoSinFirmaYDespuesFirmado` |
| Firma de otra persona o de un contenido anterior → no habilita preparación | `…firmaNoValida` y `PedidoFirmaPagoTest.firmaNoValidaSiNoEsDelAsignadoOCambiaElContenido` |
| Rechazo solo del veterinario asignado; pagado → devolución pendiente | `…rechazoDelVeterinario` |
| Permisos de la clasificación | `…permisosDeLaClasificacion` |
| Salida habitual al preparar, recuento de bultos, sin duplicados, la clínica no ve la logística | `LogisticaSalidasIntegracionTest.salidaHabitualYRecuentoDeBultos` |
| Salida manual respetada al preparar | `…salidaManualSeRespeta` |
| Clínica sin salida habitual → no se prepara hasta elegir una | `…clinicaSinSalida` |
| Cambio de salida: no si expedido ni con bultos en palet de otra salida | `…cambioDeSalidaConReglas` |
| Palet de otra salida rechazado | `RecorridoPedidoIntegracionTest.recorridoCompleto` y `…cambioDeSalidaConReglas` |
| Permisos de salidas y bultos | `…permisos` |
| Bultos por peso: 10; 24,99; 25; 25,000; 25,001; 25,01; 25,1; 49,99; 50; 50,01; 50,1; 75; 75,001; 0,001 kg | `PesoPedidoTest.limites` (14 casos) |
| Cantidades múltiples (peso por unidad × cantidad) y varias líneas | `PesoPedidoTest.cantidadesMultiples` |
| Sin errores de coma flotante (24,999 + 0,001; 8,333 × 3) | `PesoPedidoTest.sinErroresDeComaFlotante` |
| Producto sin peso: incompleto, sin bultos, no cuenta como 0 | `PesoPedidoTest.productoSinPeso` y `LogisticaSalidasIntegracionTest.bultosPorPeso` |
| Al preparar se crean los bultos que faltan; repetir no duplica; no se registra por encima de lo calculado | `…salidaHabitualYRecuentoDeBultos` y `…bultosPorPeso` |
| Varias reservas vencidas en la misma pasada: se cancelan todas y se libera su stock | `RecorridoPedidoIntegracionTest.caducidadDeVariasReservas` |
| Odoo: dos listas de firma y pago y un apartado más por salida | `OdooJsonRpcConnectorTest.apartadoPorSalida` |
| Salida prevista: la asignada manda y prever no asigna nada | `PesoPedidoTest.salidaPrevista` |
| Una unidad de más de 25 kg se señala | `PesoPedidoTest.unidadSobreElLimite` |
| La clínica no recibe datos internos, tampoco en el resumen; cantidad máxima por línea | `LogisticaSalidasIntegracionTest.salidaHabitualYRecuentoDeBultos` |
| Sobran registros en un palet: no se prepara ni se borra solo; se saca del palet (no la clínica) y se elimina | `…bultosSobrantes` |
| No se saca del palet un bulto de un pedido expedido | `…cambioDeSalidaConReglas` |
| Editar un producto sin enviar stockInicial (como el formulario) | `…bultosPorPeso` (ayudante `pesoProducto`) |

### Errores reales que encontraron estas pruebas (ya corregidos)

1. `001_esquema_inicial.sql` no se podía ejecutar dos veces por la clave
   foránea circular clínicas ↔ veterinarios.
2. Modificar las líneas de un borrador con el mismo producto fallaba por la
   clave única (pedido, producto).
3. Las observaciones admitían 2000 caracteres en el frontend y 255 en la base de datos.

## 2. Comprobación manual en el navegador (realizada)

Realizada el 2 de noviembre de 2026 con el backend en local y una base de datos
separada recién cargada:

1. `aux.sanroque` añade un producto, revisa el carrito (base, IVA 4 % y total) y lo envía a firma → «Pendiente de firma». En la pestaña Pago se explica que falta la firma y no hay botón de pagar.
2. `dr.garcia` abre el pedido → «Revisar y firmar» muestra líneas e importes. Dibuja la firma con el ratón, marca la casilla y firma → «Firmado» y pasa a la pestaña de pago.
3. Pasarela de demostración → «Simular pago fallido» → el pedido sigue Firmado y el pago aparece como fallido. «Pagar» de nuevo crea otra referencia → «Simular pago correcto» → «Listo para preparar», con la cronología completa.
4. `admin` → Pedidos: filtros por fechas, estado y clínica; columnas de Firma y Pago. Inicia la preparación. «Marcar como preparado» sin bultos muestra el error de la regla.
5. `admin` crea desde el carrito un pedido para el Hospital Mediterráneo: se propone el Dr. Martín y la ficha muestra «Creado por: Laura Sánchez (Almacén)».

Durante esta comprobación se detectaron y corrigieron: la pasarela mostraba el
resultado pedido en vez del devuelto por el servidor, el reintento reutilizaba
una referencia fallida, existía un botón «Firmar más tarde y pagar» contrario a
la regla y la firma no mostraba el contenido que se autoriza.

## 2b. Inicio, carrito y notificaciones (comprobado en el navegador)

Realizado el 2 de noviembre de 2026 con el frontend de desarrollo y una base de datos
separada recién cargada. Antes de los cambios se reprodujeron dos errores: el carrito
de `aux.sanroque` aparecía al entrar `dr.martin` en el mismo navegador, y se
podían añadir 3 unidades de un producto con 2 disponibles.

| Comprobación | Resultado |
|---|---|
| Login de cliente → `/inicio`; login de admin → `/admin` | Correcto |
| Inicio con datos reales de la clínica (pendientes, últimos pedidos, productos más pedidos) | Correcto |
| Añadir 1 unidad: aviso con producto y cantidad, «Ver carrito», contador +1 | Correcto |
| Añadir 3 unidades escritas en el campo | Correcto (contador +3) |
| Superar el stock disponible | Aviso de error; el contador no cambia |
| Cantidad 0 | Aviso de error; el contador no cambia |
| 5 pulsaciones seguidas | Como máximo 3 avisos a la vez |
| Fallo al guardar en el navegador (simulado) | Aviso de error con `role="alert"`; el contador no cambia |
| Fallo de la API al comprobar el stock del carrito (simulado) | Mensaje con «Reintentar» |
| El stock baja en el servidor con el carrito abierto | Aviso, fila marcada y botones de envío bloqueados hasta corregir |
| +/−, quitar producto, subtotales, IVA y total | Correctos |
| Separación de cuentas y persistencia del carrito | Correcto (cada usuario ve solo el suyo) |
| Filtro «Solo con stock disponible» | Corregido (antes mostraba solo los de stock bajo) |
| Móvil 375 px: sin desplazamiento horizontal; cabecera en una línea; avisos sin tapar los botones | Correcto |

## 2c. Clasificación firma/pago con los datos reales (Docker)

Tras aplicar la migración 009 y desplegar (2 de noviembre de 2026), los 6 pedidos pagados
de la base `vetstock` aparecen en «Pagados pendientes de firma»: 2 sin firma y 4 firmados
por la auxiliar con la regla antigua («Firmado por Sonia Ruiz (Auxiliar), que no es el
veterinario asignado»). En Odoo se crearon los campos `x_vetstock_*` y los dos favoritos
compartidos, y el favorito «Pagados pendientes de firma» encuentra esos pedidos. El pedido
S00001 (VSM-9) no superó la comprobación fiscal porque se creó en USD en una fase anterior.

### Prueba en vivo: pago antes de la firma (Docker + Odoo real)

Pedido de prueba #15 (2 × guantes, 20,70 €) de `aux.sanroque`, con la API real:

| Paso | VetStock | Odoo (S00007) |
|---|---|---|
| Enviar a firma y pagar (pasarela de demostración) | PENDIENTE_FIRMA · «Pagados pendientes de firma» · «Falta la firma de Dr. Antonio García» | Exportado solo; `x_vetstock_pagado=true`, `firma_valida=false`; aparece en el favorito «Pagados pendientes de firma» |
| Admin intenta preparar | Rechazado (400) | — |
| La auxiliar intenta firmar | Rechazado (403) | — |
| Firma el Dr. García | LISTO_PREPARAR · «Firmados y pagados» · listo | Mismo pedido S00007, `firma_valida=true`; pasa al favorito «Firmados y pagados» |
| Admin inicia la preparación | EN_PREPARACION · sigue «Firmados y pagados», ya no «listo» | `x_vetstock_estado=EN_PREPARACION`; un solo pedido VSM-15; estado comercial sin tocar (borrador) |

### Prueba en vivo: confirmación automática en Odoo

- S00007 (VSM-15, ya firmado y pagado): al sincronizar pasó de *Quotation* a *Sales Order*.
- Pedido nuevo #17: pagado sin firma → S00008 en *Quotation* («Pagados pendientes de firma»);
  firma del Dr. García → S00008 pasa sola a *Sales Order* («Firmados y pagados»), sin pulsar
  nada en Odoo.
- Pruebas automáticas: `OdooJsonRpcConnectorTest.pedidoFirmadoYPagadoSeConfirma` y
  `noSeConfirmaSinFirmaNiDosVecesNiSiEstaCancelado`.

### Contadores = listas (comprobado en el navegador)

Tras la corrección, cada tarjeta muestra exactamente los pedidos que abre (Docker, 2 de noviembre de 2026):

| Tarjeta | Número | Lista al pulsar |
|---|---|---|
| Inicio (Dr. García): Pendientes de firma | 4 | #13, #11, #8, #6 |
| Inicio: Pendientes de pago | 2 | #8, #6 |
| Inicio: En preparación | 1 | #15 |
| Resumen admin: Listos para preparar | 2 | #18, #17 |

### Datos de práctica en la base `vetstock` (Docker)

Se dejaron 1 o 2 pedidos de cada caso (copia previa: `_respaldos-vetstock/antes-limpieza-pedidos-20261102.sql`):
#1 borrador · #6 y #8 pendientes de firma sin pagar · #13 pagado sin firma · #11 pagado con firma
de auxiliar (no válida) · #17 y #18 firmados y pagados (listos) · #15 en preparación · #7 cancelado.
En Odoo se borraron las copias de los pedidos eliminados (S00001, S00002, S00004, S00006, S00009).

## 2d. Salidas de expedición y bultos (en vivo, 3 de noviembre de 2026)

Docker con Odoo real, sobre la base `vetstock` tras aplicar `010` (copia previa:
`_respaldos-vetstock/antes-migracion-010-20261103.sql`). Capturas en
[capturas/logistica](capturas/logistica/).

| # | Prueba | Cómo | Resultado |
|---|---|---|---|
| 1 | Código 14 en Regional y salida habitual de San Roque | Navegador: Salidas → Editar; Clínicas → selector | Correcto, guardado en la base |
| 2 | Preparar un pedido firmado y pagado | API: #17 → preparar | Salida «14 - Regional», automática, con usuario y fecha |
| 3 | Salida manual se respeta | API: #18 → Valencia → preparar | Sigue Valencia (manual) |
| 4 | Clínica sin salida | Se quitó un momento la salida habitual de San Roque; #19 | Preparar → 400 con mensaje; aviso y selector en el detalle; tras elegir salida se preparó |
| 5 | Dos bultos | #17 con BULTO-17-01 y 02; repetir 02 → 400 | «Bultos: 2» en lista y detalle |
| 6 | Pantalla de bultos | Navegador: Admin → Bultos | Aparecen con pedido, salida y palet |
| 7 | Odoo | JSON-RPC y lectura de la página (el panel no pintaba Odoo) | Formulario S00008: Bultos y Salida en solo lectura; lista VetStock con columnas Bultos y Salida |
| 8 | Reexportar | `POST /api/odoo/pedidos/17/exportar` dos veces | Sigue habiendo 1 pedido VSM-17 (mismo ID 8) y 1 línea |
| 9 | Tercer bulto | BULTO-17-03 | 3 en VetStock y 3 en Odoo |
| 10 | Palet incompatible | Bulto de #17 (Regional) a palet de Valencia | 400; al palet Regional sí; cambiar salida con bulto en palet → 400 |
| 11 | Odoo parado | `docker stop vetstock-odoo`, bulto en #18 | Bulto guardado; «pendiente de sincronizar» con el error en el detalle; tras arrancar Odoo, «Actualizar en Odoo» → 2 bultos en Odoo, sin pendiente |
| 12 | Sin firma o sin pago | #11 (firma de auxiliar) y #8 (sin firmar ni pagar) | Bloqueados (400), también registrar bultos |
| 13 | Cambiar salida habitual | San Roque → Alicante y vuelta a Regional | #17, #18 y #19 no cambian |
| 14 | Permisos | dr.garcia y aux.sanroque | 403 en salidas, bultos, palets y salida del pedido; el pedido les llega sin salida ni bultos |
| — | Expedir | #19: sin palet → 400; con palet → EXPEDIDO; cambiar salida → 400 | Odoo: EXPEDIDO, 1 bulto, 14 - Regional |

Datos que quedaron de estas pruebas: #17 y #18 PREPARADO, #19 EXPEDIDO (stock físico
descontado una vez), 6 bultos y 2 palets (PALET-VAL-01, PALET-REG-01). Con la regla de
bultos por peso (2e) sobraban 3; se limpiaron después (ver abajo).

## 2e. Bultos por peso (en vivo, 3 de noviembre de 2026)

Tras aplicar `011` y los pesos ficticios de la demo (copia previa:
`_respaldos-vetstock/antes-migracion-011-20261103.sql`):

| Prueba | Resultado |
|---|---|
| Pedido nuevo #21 (`aux.sanroque`): 3 sacos de pienso (12,5 kg) + 1 caja de guantes (0,6 kg) | 38,1 kg → **2 bultos calculados** |
| Firmado por el Dr. García, pagado (pasarela de demostración), iniciar preparación, preparar | PREPARADO, salida 14 - Regional, registros **VSM-21-B1 y VSM-21-B2** creados solos |
| Preparar otra vez / registrar un tercer bulto a mano | 400 / 400 («ya tiene los 2 bultos que le corresponden»); siguen 2 registros |
| Odoo (S00013) | Bultos 2, Peso total 38,1 kg, Salida 14 - Regional, solo lectura (lista y formulario) |
| Reexportar 2 veces | 1 solo pedido VSM-21 en Odoo y 2 registros de bultos |
| Quitar el peso de la vacuna desde Admin → Productos | El pedido #11 muestra «Cálculo incompleto: falta el peso de Vacuna antirrábica. Peso conocido: 18,5 kg»; registrar bulto → 400. Peso restaurado (0,020 kg) desde la misma pantalla |
| Pedidos de prueba anteriores | #17: 3 registros y le corresponde 1; #18: 2 y 1, todos en palets. La ficha avisa y ofrece «Sacar del palet» en cada bulto. No se ha cambiado nada (ver pendientes) |
| Limpieza de sobrantes (#17 y #18) | Copia previa `_respaldos-vetstock/antes-limpiar-bultos-sobrantes-20261103.sql`. Sacados del palet y eliminados BULTO-17-02, BULTO-17-03 y BULTO-18-02. Eliminar el último bulto de #17 → 400 (no sobra). Ahora #17 y #18 tienen 1 registro = 1 calculado (BULTO-17-01 en PALET-REG-01, BULTO-18-01 en PALET-VAL-01). Odoo: VSM-17 y VSM-18 con 1 bulto y su peso; VSM-19 reexportado (1 bulto, 0,45 kg) |
| Sacar del palet (#21) | VSM-21-B1 al palet PALET-REG-01 y sacarlo → 200 (vuelve a quedar sin palet); repetir → 400; BULTO-19-01 (pedido expedido) → 400 |

Errores encontrados y corregidos en esta prueba: editar un producto desde la pantalla fallaba
siempre («no debe ser nulo») porque el formulario no envía `stockInicial` (error antiguo, del
primer frontend); y la validación del peso en el formulario tenía mal escrita la expresión regular.

## 2f. Recorrido completo por la pantalla y primer lote de mejoras (5 de noviembre de 2026)

Docker con Odoo real. Pedido de prueba #24 (3 sacos de pienso y 1 caja de guantes):

| Paso | Cuenta | Resultado |
|---|---|---|
| Catálogo → carrito → enviar a firma | aux.sanroque | Pedido #24, 144,20 €, stock reservado |
| Revisar, dibujar firma y firmar | dr.garcia | Firmado · pendiente de pago |
| Pago fallido, reintento con otra referencia y pago correcto | dr.garcia | Dos intentos: FALLADO y CONFIRMADO; pedido listo para preparar |
| Iniciar preparación y marcar como preparado | admin | 38,1 kg → VSM-24-B1 y B2; salida 14 - Regional |
| Asignar a palet | admin | El selector solo ofrece PALET-REG-01; aviso de éxito en cada asignación |
| Expedir | admin | Stock físico del pienso de 15 a 12; Odoo S00015: EXPEDIDO, 2 bultos, 38,1 kg |

También comprobado: un usuario de otra clínica recibe 403 en pedido, firma, documentos, pago, cancelar y rechazar;
filtros conservados al volver del detalle y tras iniciar sesión de nuevo; «Limpiar filtros»; «Ningún registro coincide» frente a «Todavía no hay pedidos»;
aviso de sesión caducada; sin desbordamiento a 375 px en pedidos, bultos, productos, clínicas, mis pedidos, stock y palets; horas correctas con `TZ`.

Automatizado solo para esta prueba: los clics del navegador se hicieron en parte por código y el diálogo de confirmación de «Expedir» se aceptó automáticamente.
No comprobado: móvil o tableta real, y lectores de pantalla.

Caducidad desplegada (copia previa: `_respaldos-vetstock/antes-caducidad-20261105.sql`): en la primera pasada
se cancelaron #6 y #8, ambos sin pago desde el 31/10, con el motivo guardado. La reserva de MED-001 bajó de 11 a 9 y su stock físico
no cambió (119). Sin errores en el registro.

## 2g. Apartado por salida y paso automático al firmar (en vivo, 5 de noviembre de 2026)

| Prueba | Resultado |
|---|---|
| Pedido pagado sin firma | VetStock: pagado pendiente de firma · Odoo: presupuesto, en «Pagados pendientes de firma» |
| Lo firma el veterinario asignado, sin más acciones | VetStock: listo para preparar · Odoo: mismo pedido, ya pedido de venta, en «Firmados y pagados»; una sola copia |
| Odoo → «Pedidos por salida» | Agrupado: 14 - Regional (4 pedidos) y Valencia (1), con dirección, estado, bultos y peso ([captura](capturas/mejoras/odoo-pedidos-por-salida.jpg)) |
| VetStock → pestaña «Por salida» | Mismas salidas y totales ([captura](capturas/mejoras/vetstock-por-salida.jpg)) |
| Vista de impresión | Reglas de impresión simuladas en pantalla: sin cabecera ni filtros y la tabla cabe en el ancho. **No se ha impreso en papel** |

Después se borraron todos los pedidos en VetStock y en Odoo para empezar las pruebas de cero (copias previas en `_respaldos-vetstock/`:
`antes-borrar-todos-los-pedidos-20261105.sql` y `odoo-antes-borrar-pedidos-20261105.sql`). Se devolvió al stock físico lo que habían restado las
expediciones y las reservas quedaron a 0. Se conservan productos, clínicas, cuentas, salidas, los 2 palets vacíos y los movimientos de stock manuales.

Las copias de seguridad citadas en este documento (`_respaldos-vetstock/`) se retiraron el 5 de noviembre de 2026 a petición del alumno: ya no están disponibles.

## 2h. Web pública y solicitudes de alta (en vivo, 6 de noviembre de 2026)

Con Docker, MySQL y Odoo reales y datos ficticios. Automatizadas en `SolicitudesAltaIntegracionTest`: 22 pruebas.

| Prueba | Resultado |
|---|---|
| Web a 360, 390, 768 y 1366 px | Sin desplazamiento horizontal |
| Formulario vacío | 13 errores y foco en el primer campo |
| Email mal escrito | Error y datos conservados |
| Dos envíos simultáneos | **Fallo encontrado:** creó dos solicitudes. Corregido en navegador y servidor, con prueba concurrente añadida. La comprobación final es esa prueba automática, no se repitió el clic en el navegador |
| Llegada a Odoo y texto con HTML | Aparece en la sección, y `<b>` se muestra como texto |
| Rechazar en Odoo | No crea clínica ni contacto |
| Aprobar en Odoo | Clínica y veterinario creados, cuenta inactiva, empresa y persona en Odoo, sin usuario interno |
| Aprobar dos veces | Una sola clínica |
| Odoo parado al enviar | Referencia entregada y error registrado; se envió sola al volver Odoo, un único registro |
| Usuario de Odoo sin el grupo | No lee, no aprueba, no ve el menú |
| Revisor | Lee y escribe observaciones; no crea, no borra, no cambia estados a mano |
| Activación | Sin activar da 401; activada entra con rol VETERINARIO; el enlace no sirve dos veces; un veterinario recibe 403 en el listado |

Fallo parcial de Odoo durante la aprobación, duplicados y firmas no válidas: solo en las pruebas automáticas, con el conector simulado.
El módulo de Odoo no tiene pruebas automáticas propias: se comprobó en vivo como indica la tabla.

## 2i. App Android (en vivo, 6 de noviembre de 2026)

En el emulador Pixel 6 (Android 37) contra el backend de Docker, con cuentas de demostración.

| Prueba | Resultado |
|---|---|
| Compilación `assembleDebug` | Correcta |
| Contraseña incorrecta | Mensaje del servidor, sin entrar |
| Catálogo, búsqueda y detalle | Correctos; la búsqueda se envía al servidor |
| Añadir al carrito y crear pedido enviado a firma | Pedido #28 con base, IVA y total calculados por el servidor |
| Cerrar sesión y entrar como veterinario asignado | Ve el pedido en "pendientes de mi firma" |
| Firmar sin dibujar | Aviso, no se envía |
| Firmar con dibujo y confirmación | Firma válida; el pedido desaparece de pendientes |
| Otra veterinaria de la clínica | No ve pendientes ni el botón de firmar |
| Backend parado | Mensaje de conexión; al volver, entra |
| Sesión perdida al reiniciar el backend | Vuelve al inicio de sesión con aviso |
| Cuenta de administración | No entra; remite al portal web |

**No probado:** móvil físico, versión release, guardar borrador y su fallo parcial desde la app, doble pulsación real al crear el pedido, y giro de pantalla. Sin pruebas automáticas propias.

## 3. Guía de comprobación manual (para repetirla)

Reinicia los datos (ver [instalacion-windows.md](instalacion-windows.md), apartado 4) y sigue el
[guion de defensa](guion-defensa.md). Además:

- Móvil: abre la aplicación con el modo responsive del navegador (375 px) y firma con el dedo o simulando táctil.
- Entra como `aux.mediterraneo` e intenta abrir `/pedidos/2` → acceso denegado.
- Sube un PDF como admin en un pedido (pestaña Documentos) y descárgalo como `aux.sanroque`.
- Admin → Bultos → registra un bulto del pedido → en el pedido, «Marcar como preparado» (toma la salida de la clínica) → Palets → crea un palet con esa misma salida → asigna el bulto → «Expedir» → el stock físico baja en Admin → Stock.

## 4. No comprobado (pendiente)

- Firma con el dedo en un **móvil o tableta real** (solo con ratón y modo responsive).
- Importación del proyecto en **IntelliJ** (no se puede comprobar desde aquí).
- Arranque completo con `docker compose up` usando la nueva versión **sobre el volumen de datos antiguo** (requiere aplicar antes las migraciones 006–008; ver la guía).
- Lectores de pantalla y accesibilidad más allá de etiquetas y estados en texto.
- Pruebas de carga.
