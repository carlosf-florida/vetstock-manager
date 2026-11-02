# Pendientes

Actualizado el 2 de noviembre de 2026. Entrega: finales de febrero de 2027.

## Para la entrega académica (recomendado)

| Prioridad | Tarea |
|---|---|
| Alta | Probar la firma con el dedo en un móvil o tableta real |
| Alta | Comprobar la importación en IntelliJ (ver `instalacion-windows.md`, apartado 7) |
| Alta | Ensayar el guion de defensa con los datos recién cargados |
| Media | Edición y baja (desactivación) de usuarios y clínicas en administración |
| Media | Botón «Registrar bulto» directamente desde la ficha del pedido (ahora se hace en Admin → Bultos) |
| Media | Marcar un palet como expedido desde la interfaz (el endpoint existe: `PATCH /api/palets/{id}/expedir`) |
| Media | Informe Power BI en Desktop sobre las vistas, si hay acceso a Power BI |
| Baja | Paginación en el servidor si crecen mucho los pedidos (ahora la tabla pagina en el cliente) |
| Baja | Actualizar `backend/README.md` y `docs/arquitectura.md` si se cambian más reglas |

## Decisiones que conviene confirmar

- Tratamiento de un pago confirmado que llegue para un pedido ya cancelado o
  caducado: ahora se marca FALLADO. En la realidad habría que **devolver el
  dinero**, y eso está fuera del alcance. La caducidad respeta los pagos en curso
  (60 min) para que sea muy improbable.
- Cancelación de pedidos pagados: no permitida (sin reembolsos).

## Para producción (no se ha hecho)

1. **Pasarela real** (Redsys/Bizum): contrato, credenciales, adaptador y backend
   público con HTTPS (ver `firma-y-pagos.md`). Desactivar la pasarela simulada.
2. **Seguridad:** activar CSRF (o usar tokens), HTTPS obligatorio, cookies
   `Secure`/`SameSite`, política de contraseñas, bloqueo tras intentos fallidos,
   cambio de las cuentas de demostración y secretos en un gestor.
3. **Firma:** si se necesitara validez legal, usar un proveedor de firma avanzada
   o cualificada. Guardar las imágenes cifradas o en un almacenamiento privado.
4. **Facturación fiscal** real (numeración, datos fiscales, Verifactu/TicketBAI
   según corresponda): ahora los documentos son PDF de prueba.
5. **Migraciones automáticas** (Flyway o Liquibase) en lugar de scripts manuales.
6. Copias de seguridad, monitorización, registros de auditoría y despliegue.
7. Revisión de protección de datos (RGPD) de clínicas y usuarios.
8. Reembolsos y cancelación de pedidos pagados, con su flujo contable.
