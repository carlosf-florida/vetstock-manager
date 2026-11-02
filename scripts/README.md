# Scripts

## pruebas_flujo.sh

Prueba el recorrido completo de un pedido contra el backend arrancado (`localhost:8080`), con la base de datos recién cargada:

```bash
mysql -u root -p < ../database/migrations/001_esquema_inicial.sql
bash pruebas_flujo.sh
```

**44 comprobaciones**, todas con el resultado esperado:

1. Sesiones y roles (login, 401 sin sesión, 403 a informes para un auxiliar)
2. Creación de borradores y permisos de firma (auxiliar → 403)
3. Reserva de stock atómica al enviar a firma
4. Firma solo del veterinario asignado (otro → 403; dos veces → 400)
5. Copia inmutable de la firma
6. Pagos: iniciar, FALLADO, pedido intacto, reintento con referencia nueva, confirmación, notificación duplicada sin efectos y token incorrecto → 403
7. Preparación y expedición con descuento único de stock
8. Stock insuficiente y aislamiento entre clínicas
9. Bultos, palets y destinos
10. Documentos (subir, descargar con permiso, 403 sin él, no-PDF rechazado)
11. Informes y dashboard

Sale con código 0 solo si todas pasan (útil para integración continua).
