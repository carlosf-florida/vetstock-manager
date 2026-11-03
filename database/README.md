# Base de datos (MySQL)

Todo lo relativo a la base de datos de VetStock Manager vive en esta carpeta.

## Contenido

- `migrations/001_esquema_inicial.sql` — esquema actual completo + datos ficticios. Borra y recrea las tablas de `vetstock`: se puede repetir para reiniciar la demo (desactiva las claves foráneas solo durante el borrado).
- `migrations/002_veterinario_responsable.sql` — añade el veterinario responsable asignado a cada clínica sin borrar datos existentes.
- `migrations/003_reparar_textos_utf8.sql` — repara textos de prueba mal codificados y guarda sus valores anteriores.
- `migrations/004_firma_pago_independientes.sql` — añade colegiado opcional a la firma y seguimiento de sincronización del pedido, sin borrar datos. Se puede repetir.
- `migrations/005_iva_pedidos.sql` — conserva 0% en pedidos existentes y configura 4% para los nuevos. Añade `pedidos.iva_porcentaje`, sin recobrar pagos ni modificar firmas históricas.
- `migrations/006_modificacion_pedidos.sql` — guarda quién cambió por última vez las líneas de un pedido y cuándo. Se puede repetir.
- `migrations/007_caducidad_reservas.sql` — añade `pedidos.motivo_cancelacion` para la caducidad de reservas (72 h sin pago). Se puede repetir.
- `migrations/008_vistas_powerbi.sql` — vistas de solo lectura para Power BI (ver `docs/power-bi.md`). Se puede repetir.
- `migrations/009_rechazo_devolucion.sql` — añade `pedidos.devolucion_pendiente` (pedido pagado y después rechazado por el veterinario). Se puede repetir.
- `migrations/010_salidas_expedicion.sql` — salidas de expedición: `destinos.codigo`, `clinicas.destino_habitual_id` y salida del pedido (`pedidos.destino_id`, `destino_manual`, `destino_asignado_por_id`, `fecha_asignacion_destino`). No asigna salida a pedidos existentes. Se puede repetir (ver `docs/logistica-salidas-bultos.md`).
- `migrations/011_peso_productos.sql` — `productos.peso_unitario_kg` (kg por unidad, 3 decimales; NULL = desconocido) para calcular los bultos. No rellena pesos. Se puede repetir.
- `demo/pesos_ficticios_demo.sql` — **pesos FICTICIOS** solo para la demo; no pisa pesos ya introducidos.

**Instalaciones existentes:** aplicar en orden las migraciones que falten (002–011) y no volver a ejecutar el esquema inicial si hay datos que conservar. Firma y pago pueden llegar en cualquier orden; solo se prepara con firma válida y pago completo (ver `docs/modelo-y-reglas.md`). Instrucciones para Windows: `docs/instalacion-windows.md`. El total nuevo es base más IVA calculado por línea, con redondeo decimal; ver `docs/iva-pedidos.md`.

## Cómo cargarla

```bash
mysql -u root -p < migrations/001_esquema_inicial.sql
```

El backend se conecta con las credenciales de `backend/src/main/resources/application.properties` (sobrescribibles con las variables de entorno `DB_URL`, `DB_USER`, `DB_PASSWORD`). Hibernate está en `ddl-auto=none`: **las tablas solo se crean con este script**, nunca por el backend.

## Tablas (15)

| Tabla | Qué guarda | Relaciones |
|---|---|---|
| `usuarios` | Credenciales de acceso (contraseña cifrada con **BCrypt**) y rol (`ADMIN`, `VETERINARIO`, `AUXILIAR`) | 1 por cuenta |
| `clinicas` | Clínicas clientes y su veterinario responsable de firma | N:1 opcional con `veterinarios` |
| `veterinarios` | Datos de veterinario (nº de colegiado) | 1:1 con `usuarios`; N:1 con `clinicas` |
| `auxiliares` | Auxiliares que crean pedidos de su clínica y los dejan pendientes de firma | 1:1 con `usuarios`; N:1 con `clinicas` |
| `productos` | Catálogo: `stock_actual` (físico), `stock_reservado` (en pedidos no expedidos) y `stock_minimo` (aviso de reposición) | — |
| `pedidos` | Estado operativo, fechas independientes de firma/pago y `odoo_sincronizacion_pendiente`/`odoo_ultimo_error` | N:1 con `clinicas`, `veterinarios` (responsable) y `usuarios` (creador) |
| `lineas_pedido` | Líneas: producto, cantidad y **precio unitario copiado** en el momento del pedido (histórico inmune a cambios de precio) | N:1 con `pedidos` y `productos` |
| `firmas` | Una confirmación por pedido: firmante real en `usuario_id`, tipo (`DIBUJO`/`IMAGEN`), imagen PNG/JPG, colegiado opcional identificativo y copia inmutable | 1:1 con `pedidos`; N:1 con `usuarios` |
| `pagos` | Intentos de pago: referencia única, importe, moneda, proveedor, estado y nº de intentos | N:1 con `pedidos` (varios intentos = varios registros) |
| `destinos` | Salidas de expedición: código opcional y único (p. ej. `14`) y nombre | — |
| `palets` | Palets de expedición con su salida y fecha de expedición | N:1 con `destinos` |
| `bultos` | Bultos individuales asignados a un palet | N:1 con `palets` y `pedidos` |
| `documentos` | PDF adjuntos (máx. 10 MB) guardados en la base de datos, con tipo y quién lo subió | N:1 con `pedidos` y `usuarios` |
| `movimientos_stock` | Historial de entradas/salidas manuales y consumos por expedición | N:1 con `productos` y `usuarios` |
| `odoo_log` | Registro de cada operación con el ERP Odoo (tipo, referencia, éxito/fallo, detalle e id asignado en Odoo) | — |

## Reglas de integridad

- Claves foráneas en todas las relaciones, con `ON DELETE RESTRICT` para que no se borre nada con historial colgando.
- `CHECK` en estados (`ck_ped_estado`, `ck_pag_estado`, `ck_fir_tipo`...), categorías y valores no negativos.
- `UNIQUE` en usernames, códigos de producto, identificadores de bulto/palet y referencias de pago.

## La reserva de stock

`productos` lleva dos columnas y todas las transiciones son atómicas (una sola sentencia SQL condicionada dentro de una transacción, de modo que dos pedidos simultáneos no puedan reservar la misma unidad):

- **Reservar** (al enviar a firma): `stock_reservado += n` si hay `stock_actual - stock_reservado >= n`.
- **Liberar** (al cancelar): `stock_reservado -= n`.
- **Descontar** (al expedir, una sola vez): `stock_actual -= n`, `stock_reservado -= n`.

## Datos de prueba incluidos

- 3 clínicas, 3 veterinarios, 1 auxiliar y 1 administrador.
- Productos con **pesos ficticios** de demostración (no medidos).
- 10 productos de las 3 categorías (MED, CLI, ALI) con stock variado (hay 3 bajo mínimo para ver el aviso).
- 4 pedidos en distintos estados (borrador, pendiente de firma, en preparación y expedido con su histórico completo).
- 3 salidas (Valencia, Alicante y Regional con código 14, salida habitual de San Roque), movimientos de stock de ejemplo.

### Cuentas de prueba

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `dr.garcia` | `vet123` | VETERINARIO |
| `dra.lopez` | `vet123` | VETERINARIO |
| `dr.martin` | `vet123` | VETERINARIO |
| `aux.sanroque` | `aux123` | AUXILIAR |

## Notas para ampliaciones

- **Integración con Odoo**: `productos`, `clinicas` y `pedidos` llevan columnas `odoo_id` (y fecha de sincronización) para enlazar cada fila con su homólogo en el ERP; `odoo_log` deja traza de cada envío.
- Los informes del backend leen directamente estas tablas; para conectar **Power BI**, basta con conectarlo a MySQL y apuntar a `lineas_pedido`, `pedidos` y `productos`.
- Los PDF se guardan en la base de datos por simplicidad del prototipo; en producción sería más habitual una carpeta o un almacenamiento de objetos.
