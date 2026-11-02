# Scripts

## Pruebas

Las pruebas del recorrido completo ya **no** son scripts de shell: son pruebas
JUnit de integración que arrancan la aplicación y usan MySQL real (base
`vetstock_test`, que se crea sola). Se ejecutan con:

```bash
cd backend
mvnw.cmd test
```

Qué comprueban y cómo se interpretan: [`docs/pruebas.md`](../docs/pruebas.md).

Los scripts antiguos (`pruebas_flujo.sh` y los `.py` de Odoo) están en
[`anteriores/`](anteriores/LEEME.md): se conservan como histórico, pero
corresponden a reglas que ya han cambiado (pago antes de firma, token en texto).

## arrancar_odoo.sh

Prepara un servidor Odoo 18 de pruebas para la integración ERP **opcional**
(adaptador `ODOO_MODO=REAL`). No es necesario para el recorrido principal: por
defecto se usa el adaptador simulado.
