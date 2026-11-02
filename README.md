# VetStock Manager

Aplicación web para una distribuidora (ficticia) de productos veterinarios. Tiene
un **portal para las clínicas** (catálogo, carrito, pedidos, firma del veterinario
y pago) y un **panel de administración** (clínicas y usuarios, productos y stock,
pedidos, documentos, bultos, palets, destinos e informes).

> Proyecto final individual de 2º DAM. No representa a ninguna empresa real.
> Los datos son ficticios, la pasarela de pago es **de demostración** (no cobra)
> y la firma es una firma electrónica simple **sin validez legal certificada**.

**Regla principal:** ningún pedido pasa a preparación sin la **firma del veterinario
asignado** y el **pago confirmado** por la notificación del proveedor.

## Recorrido principal

```
Cliente/auxiliar crea pedido ─► Envía a firma (reserva stock)
   ─► Veterinario asignado revisa y firma ─► La clínica paga (pasarela demo)
   ─► El backend verifica la notificación firmada ─► LISTO_PREPARAR
   ─► Admin: preparación, bultos, palet y destino ─► PREPARADO ─► EXPEDIDO (descuenta stock)
```

Pedidos sin pago confirmado a las 72 h se cancelan solos y liberan la reserva.

## Tecnologías

Java 21 · Spring Boot 4.1 · Maven (wrapper incluido) · MySQL 8 · React 18 + Vite · JUnit 5 + MockMvc · Docker (opcional).

## Puesta en marcha rápida (Windows)

Guía completa paso a paso: **[docs/instalacion-windows.md](docs/instalacion-windows.md)**.

```powershell
docker compose up -d --build        # MySQL + backend + frontend (+ Odoo opcional)
```

Abrir <http://localhost:5173>. Sin Docker: MySQL local + `backend\mvnw.cmd spring-boot:run` + `npm run dev` en `frontend` (ver la guía).

## Cuentas de demostración (ficticias)

| Usuario | Contraseña | Rol | Clínica |
|---|---|---|---|
| `admin` | `admin123` | Administración | — |
| `dr.garcia` | `vet123` | Veterinario responsable | Clínica Veterinaria San Roque |
| `dra.lopez` | `vet123` | Veterinaria (no responsable) | Clínica Veterinaria San Roque |
| `aux.sanroque` | `aux123` | Auxiliar | Clínica Veterinaria San Roque |
| `dr.martin` | `vet123` | Veterinario responsable | Hospital Veterinario Mediterráneo |
| `aux.mediterraneo` | `aux123` | Auxiliar | Hospital Veterinario Mediterráneo |

Solo sirven para la demo local; en cualquier otro entorno deben cambiarse.

## Pruebas

```powershell
cd backend
.\mvnw.cmd test
```

53 pruebas automáticas (unitarias + integración contra MySQL real en la base
`vetstock_test`). Resultados y guía manual: [docs/pruebas.md](docs/pruebas.md).

## Documentación

| Documento | Contenido |
|---|---|
| [Alcance y requisitos](docs/alcance-y-requisitos.md) | Qué es, usuarios y permisos, requisitos y su estado real |
| [Product Backlog actual](docs/product-backlog-actual.md) | Historias alineadas con lo implementado |
| [Modelo de datos y reglas](docs/modelo-y-reglas.md) | Tablas, relación usuario-clínica-veterinario, estados y reglas de negocio |
| [API REST](backend/API.md) | Endpoints, permisos y ejemplos |
| [Instalación en Windows](docs/instalacion-windows.md) | JDK, MySQL, variables de entorno, arranque, IntelliJ |
| [Firma y pagos](docs/firma-y-pagos.md) | Qué es real, qué es simulado y qué falta para producción |
| [Pruebas](docs/pruebas.md) | Pruebas ejecutadas, guía manual y pendientes |
| [Power BI](docs/power-bi.md) | Vistas preparadas y cómo conectarse sin publicar datos |
| [Pendientes](docs/pendientes.md) | Lo que falta y lo necesario para producción |
| [Guion de defensa](docs/guion-defensa.md) | Demo de 10 minutos para clase |
| [Guía del proyecto](docs/guia-del-proyecto.md) | Módulos, capas, patrones y qué archivo tocar |
| [Base de datos](database/README.md) | Scripts, migraciones y datos ficticios |
| [Frontend](frontend/README.md) | Páginas y estructura |
| [IVA](docs/iva-pedidos.md) | Decisión sobre el IVA del prototipo |
| [Scrum (sprint 1)](docs/scrum/README.md) | Planificación inicial (histórico) |

## Estructura

```
backend/    API REST Spring Boot (paquete es.florida.vet)
frontend/   React + Vite
database/   migrations/001 (esquema + datos) … 008 (vistas Power BI)
docs/       documentación del proyecto
scripts/    arrancar_odoo.sh (Odoo opcional)
historico/  prototipo v1 (solo consulta, no se usa)
docker/     imagen de Odoo (integración ERP opcional)
```

La integración con **Odoo 18** es opcional (adaptador `ODOO_MODO=SIMULADO` por defecto); ver [docs/guia-del-proyecto.md](docs/guia-del-proyecto.md).
