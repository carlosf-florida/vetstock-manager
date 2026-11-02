# VetStock Manager

Gestión de pedidos y stock para clínicas veterinarias: los clientes (veterinarios y auxiliares) preparan y firman sus pedidos desde una portal web, y el almacén los prepara, agrupa en bultos y palets y los expide. Todo el ciclo —borrador, firma digital, pago, preparación y expedición— queda registrado con sus fechas.

Este proyecto es la evolución del prototipo original: el mismo enunciado reimplementado con **frontend React**, **backend Spring Boot** y **MySQL**, y documentado en `docs/`.

## Estructura

```
vetstock-manager/
├── backend/            API REST (Spring Boot, Java 21, Maven)
│   ├── README.md       Cómo funciona y cómo arrancarla
│   └── API.md          Contrato completo de la API REST
├── frontend/           Interfaz web (React 18 + Vite)
│   └── README.md       Páginas, rutas y estructura del código
├── database/           MySQL: todo lo de la base de datos
│   ├── README.md       Tablas, reglas, reserva de stock y datos de prueba
│   └── migrations/001_esquema_inicial.sql   Esquema + datos (un solo script)
├── docker-compose.yml   Levanta MySQL + PostgreSQL + Odoo + backend + frontend
├── docs/               arquitectura.md: capas y patrones de diseño
├── scripts/            pruebas_flujo.sh (50 comprobaciones) y arrancar_odoo.sh
│   └── README.md       Qué prueba cada bloque
└── version_anterior/   Páginas estáticas del prototipo v1 (referencia)
```

Cada módulo está documentado en su propio `README.md`; este archivo es el índice general.

## Requisitos

- Java 21 (JDK) y Maven (o usar el wrapper `./mvnw` incluido)
- MySQL 8 en local
- Node.js 18+ para el frontend
- **Opcional**: un servidor Odoo 18 para la integración ERP real
  (`bash scripts/arrancar_odoo.sh` lo prepara todo; sin él funciona el adaptador simulado)

## Puesta en marcha

### Opción recomendada: Docker Desktop

Con Docker Desktop instalado, desde la carpeta raíz del proyecto:

```bash
docker compose up -d --build
```

La primera vez puede tardar varios minutos porque construye Spring Boot y React
y prepara la base de datos de Odoo. Después se entra en:

- Aplicación VetStock: <http://localhost:5173>
- Odoo: <http://localhost:8069>
- API: <http://localhost:8080>

Para ver el arranque: `docker compose logs -f odoo backend frontend`.
Para apagarlo sin borrar datos: `docker compose down`. Para borrar también las
bases de datos y empezar desde cero: `docker compose down -v`.

Si quieres escribir literalmente `http://odoo` en el navegador, añade esta línea
al archivo hosts de tu ordenador (requiere permisos de administrador):

```text
127.0.0.1 odoo
```

En Windows es `C:\Windows\System32\drivers\etc\hosts`; en macOS/Linux es
`/etc/hosts`. Como el puerto publicado es el 8069, la dirección completa será
<http://odoo:8069>.

### Arranque manual sin Docker

1. **Base de datos** (ajusta usuario/clave si no usas root/root):

   ```bash
   mysql -u root -p < database/migrations/001_esquema_inicial.sql
   ```

   Las credenciales se pueden cambiar con variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`) sin tocar código.

2. **Backend** (puerto 8080):

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

3. **Frontend** (puerto 5173, con proxy hacia el backend):

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

4. Abre <http://localhost:5173>.

## Cuentas de prueba

| Usuario | Contraseña | Rol | Clínica |
|---|---|---|---|
| `admin` | `admin123` | ADMIN (almacén) | — |
| `dr.garcia` | `vet123` | VETERINARIO | Clínica San Roque |
| `dra.lopez` | `vet123` | VETERINARIO | Clínica San Roque |
| `dr.martin` | `vet123` | VETERINARIO | Clínica Miravet |
| `aux.sanroque` | `aux123` | AUXILIAR | Clínica San Roque |
| `aux.miravet` | `aux123` | AUXILIAR | Clínica Miravet |

## Qué puede hacer cada rol

- **ADMIN (almacén)**: catálogo y stock, movimientos de entrada/salida, clínicas y cuentas de clientes, ver todos los pedidos, iniciar preparación, preparar, expedir, bultos/palets/destinos, adjuntar documentos PDF, informes.
- **VETERINARIO**: catálogo y pedidos de SU clínica, firmar SOLO los pedidos que le corresponden (dibujo táctil o imagen), ver la copia inmutable de lo firmado, descargar documentos.
- **AUXILIAR**: catálogo y pedidos de SU clínica, crear y modificar borradores, confirmar, pagar y firmar con su propia identidad. El veterinario sigue siendo el responsable, pero nunca se le atribuye la firma del auxiliar.

## Flujo completo de un pedido

```
BORRADOR → PENDIENTE_FIRMA
  firma primero → FIRMADO → pago → LISTO_PREPARAR
  pago primero  → PENDIENTE_FIRMA (pagado) → firma → LISTO_PREPARAR
LISTO_PREPARAR → EN_PREPARACION → PREPARADO → EXPEDIDO
```

- Al **enviar a firma** se reserva el stock (de forma atómica).
- **Firma interna**: responsable o auxiliar de la clínica con su firma propia, por ratón o PNG/JPG, con identidad real y copia inmutable. El colegiado opcional solo identifica; no es contraseña ni sustituye a la firma.
- **Pago** (SIMULADO): se puede pagar antes o después de firmar. Una notificación confirmada solicita exportar a Odoo. Solo firma y pago completados habilitan preparación; notificaciones repetidas no duplican efectos.
- Al **expedir** se descuenta el stock físico, una sola vez.

## Pruebas

Con el backend arrancado y la base de datos recién cargada:

```bash
bash scripts/pruebas_flujo.sh
```

El script original recorre sesiones, roles, permisos por clínica, stock, firma, pagos, bultos, documentos, informes y Odoo. Para el flujo nuevo se añaden `scripts/pruebas_firma_pago_odoo.py` (19 comprobaciones con Odoo real), `scripts/pruebas_reintento_odoo.py` (3 de recuperación) y `scripts/pruebas_iva_odoo.py` (IVA, euros y redondeo por línea). El backend cuenta con 33 pruebas JUnit actuales. Ejecutar únicamente con datos de demostración; las pruebas crean pedidos y pagos simulados.

Los pedidos nuevos aplican un IVA del 4% configurado para este prototipo, añadido a precios netos, y se pagan/exportan en EUR. Carrito, ficha y pasarela muestran base, IVA y total. Los pedidos históricos conservan su tasa/importe. Ver `docs/iva-pedidos.md` y la migración `005_iva_pedidos.sql`; no ejecutar nuevamente el esquema inicial sobre datos existentes.

## Integración con Odoo (ERP)

La integración con el ERP sigue el **patrón adaptador** y funciona de dos maneras:

- `SIMULADO` (por defecto): adaptador en memoria con el mismo contrato, para trabajar sin servidor.
- `REAL`: JSON-RPC con Odoo 18. Sincroniza catálogo (`product.template`) y clínicas (`res.partner`). Exporta pedidos pagados como `sale.order`, incluso por firmar, y actualiza el mismo ID tras firma o logística. Las notas muestran estado de firma, responsable y firmante real. No cambia el estado comercial de Odoo ni registra cobros bancarios reales. Si falla el ERP, conserva el pago y deja el envío pendiente para reintento manual.

Para probarla: arranca Odoo con `bash scripts/arrancar_odoo.sh` y levanta el backend con `ODOO_MODO=REAL`. En la web, el menú **Admin → Odoo** muestra el estado, lanza las sincronizaciones y consulta el registro de operaciones.

Más información en [`docs/arquitectura.md`](docs/arquitectura.md), el contrato REST en [`backend/API.md`](backend/API.md) y la base de datos en [`database/README.md`](database/README.md).

## Simplificaciones declaradas (prototipo académico)

- El proveedor de pago es **simulado**, no una integración bancaria real (ver `docs/arquitectura.md`).
- Las credenciales del servidor Odoo de pruebas van en variables de entorno (`ODOO_*`); en producción usaríamos una cuenta de API dedicada.
- Los "documentos de factura" son adjuntos de demostración, no facturas fiscales válidas.
- CSRF desactivado para simplificar el frontend React.
- Los informes se calculan con SQL sobre las tablas del prototipo; la conexión con Power BI queda como ampliación natural.

## Documentación SCRUM

La planificación Scrum Lite se guarda en la carpeta `docs/scrum/`.
