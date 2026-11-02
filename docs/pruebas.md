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

- Fecha: 2 de noviembre de 2026 · Windows 11 · Temurin JDK 21.0.8 · MySQL 8.4 (Docker).
- Resultado: **62 pruebas, 0 fallos, 0 errores, 0 omitidas** (`BUILD SUCCESS`).
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

## 3. Guía de comprobación manual (para repetirla)

Reinicia los datos (ver [instalacion-windows.md](instalacion-windows.md), apartado 4) y sigue el
[guion de defensa](guion-defensa.md). Además:

- Móvil: abre la aplicación con el modo responsive del navegador (375 px) y firma con el dedo o simulando táctil.
- Entra como `aux.mediterraneo` e intenta abrir `/pedidos/2` → acceso denegado.
- Sube un PDF como admin en un pedido (pestaña Documentos) y descárgalo como `aux.sanroque`.
- Admin → Bultos → registra un bulto del pedido → Palets → crea un palet con destino Valencia → asigna el bulto → en el pedido, «Marcar como preparado» y «Expedir» → el stock físico baja en Admin → Stock.

## 4. No comprobado (pendiente)

- Firma con el dedo en un **móvil o tableta real** (solo con ratón y modo responsive).
- Importación del proyecto en **IntelliJ** (no se puede comprobar desde aquí).
- Arranque completo con `docker compose up` usando la nueva versión **sobre el volumen de datos antiguo** (requiere aplicar antes las migraciones 006–008; ver la guía).
- Integración real con Odoo en esta revisión (sus pruebas unitarias sí pasan).
- Lectores de pantalla y accesibilidad más allá de etiquetas y estados en texto.
- Pruebas de carga.
