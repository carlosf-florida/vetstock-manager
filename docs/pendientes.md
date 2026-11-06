# Pendientes

Actualizado el 5 de noviembre de 2026. Entrega: finales de febrero de 2027.

## Para la entrega académica (recomendado)

| Prioridad | Tarea |
|---|---|
| Alta | Probar la firma con el dedo en un móvil o tableta real |
| Alta | Comprobar la importación en IntelliJ (ver `instalacion-windows.md`, apartado 7) |
| Alta | Ensayar el guion de defensa con los datos recién cargados |
| Media | Edición y baja (desactivación) de usuarios y clínicas en administración |
| Media | Marcar un palet como expedido desde la interfaz (el endpoint existe: `PATCH /api/palets/{id}/expedir`) |
| Media | Informe Power BI en Desktop sobre las vistas, si hay acceso a Power BI |
| Media | Hacer la sincronización con Odoo en segundo plano: ahora se ejecuta dentro de la petición y confirmar un pago tarda más mientras Odoo responde |
| Media | Definir la regla para productos cuya unidad pesa más de 25 kg (ahora solo se avisa) |
| Baja | Página de detalle de producto (no existe; la tarjeta del catálogo muestra los datos) |
| Media | Sustituir los pesos ficticios de la demo por pesos reales de los productos |
| Media | Confirmar los códigos de las salidas Valencia y Alicante (solo Regional = 14 está confirmado) y la salida habitual del resto de clínicas |
| Baja | Captura de Odoo con las columnas Bultos y Salida (en esta revisión se comprobó leyendo la página) |
| Baja | Paginación en el servidor si crecen mucho los pedidos (ahora la tabla pagina en el cliente) |
| Baja | Actualizar `backend/README.md` y `docs/guia-del-proyecto.md` si se cambian más reglas |

## Decisiones que conviene confirmar

- Tratamiento de un pago confirmado que llegue para un pedido ya cancelado o
  caducado: ahora se marca FALLADO. En la realidad habría que **devolver el
  dinero**, y eso está fuera del alcance. La caducidad respeta los pagos en curso
  (60 min) para que sea muy improbable.
- Cancelación de pedidos pagados: no permitida (sin reembolsos).

## Para producción (no se ha hecho)

- App Android: pago (depende de elegir un proveedor real), prueba en un móvil físico, versión release firmada y HTTPS. Ver `android/README.md`.

- Web pública y solicitudes: responsable del tratamiento, base legal, plazo de conservación y aviso legal, además de HTTPS y límite de envíos detrás de proxy. Ver `solicitudes-alta.md`.

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
