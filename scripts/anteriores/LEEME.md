# Scripts de prueba anteriores (sustituidos)

Estos scripts se escribieron para versiones anteriores del proyecto y **ya no
reflejan las reglas actuales**:

- Permitían pagar antes de la firma (ahora la firma del veterinario asignado
  es obligatoria antes del pago).
- Autenticaban la notificación de pago con un token en texto (ahora se usa una
  firma HMAC-SHA256 del cuerpo, cabecera `X-Firma-Notificacion`).
- Llamaban a la pasarela simulada sin sesión (ahora exige la sesión de la clínica).
- Necesitan `python3`.

Se conservan como histórico del desarrollo. Las comprobaciones equivalentes
están ahora en las pruebas de integración JUnit, que se ejecutan con
`mvnw.cmd test` (ver `docs/pruebas.md`):

- `backend/src/test/java/es/florida/vet/integracion/RecorridoPedidoIntegracionTest.java`
- `backend/src/test/java/es/florida/vet/integracion/PermisosIntegracionTest.java`
