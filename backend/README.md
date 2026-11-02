# Backend · API REST (Spring Boot)

API REST de VetStock Manager: aquí vive TODA la lógica de negocio (importes, stock, permisos, estados, pagos). El frontend React solo es la vista.

## Arrancar

```bash
./mvnw spring-boot:run        # puerto 8080
```

Requiere MySQL cargado (ver [`../database/README.md`](../database/README.md)). Credenciales configurables con `DB_URL`, `DB_USER`, `DB_PASSWORD` y `PAGOS_TOKEN_NOTIFICACION`.

## Documentación de la API

- **[API.md](API.md)** — contrato completo: cada endpoint, quién puede llamarlo, cuerpos y reglas de negocio.

## Estructura

```
src/main/java/es/florida/vet
├── config/        Seguridad (Spring Security: roles, login, CORS)
├── controller/    Controladores REST (uno por módulo funcional)
├── dto/            Objetos de entrada/salida con validación
├── entity/        Entidades JPA (mapean las tablas de MySQL)
├── enums/         Rol, estados del pedido, tipos...
├── exception/     Excepciones de negocio + manejador global
├── odoo/          Integración con el ERP Odoo (patrón adaptador)
├── repository/    Acceso a datos (Spring Data JPA)
├── service/       Reglas de negocio (el corazón del sistema)
└── util/          BCrypt, fechas
```

## Puntos clave

- **Máquina de estados** del pedido en `enums/EstadoPedido.java` (`puedePasarA`); los servicios no pueden saltarse transiciones.
- **Reserva de stock atómica** al enviar a firma y **descuento único** al expedir (SQL condicionado en transacción).
- **Firma** solo del veterinario asignado, con copia inmutable JSON del pedido.
- **Pagos con patrón adaptador**: `service/PagoService.java` habla con un proveedor SIMULADO; la notificación es un webhook autenticado por token, idempotente. Sustituir por Redsys = reescribir solo ese adaptador.
- **Odoo con patrón adaptador**: `odoo/OdooConnector` con dos implementaciones (`SIMULADO` en memoria y `REAL` por JSON-RPC contra un servidor Odoo 18, seleccionables con `vetstock.odoo.modo`). Sincroniza catálogo y clínicas y exporta pedidos expedidos como `sale.order`, dejando traza en `odoo_log`.
- **Seguridad en dos capas**: roles por URL (SecurityConfig) + pertenencia a la clínica (servicios). BCrypt para contraseñas. CSRF desactivado (simplificación académica declarada).
