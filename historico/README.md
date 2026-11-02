# Histórico

Material de versiones anteriores que se conserva para mostrar cómo ha
evolucionado el proyecto. **Nada de esta carpeta se usa al compilar ni al
arrancar la aplicación actual.**

## prototipo-v1/

Primera versión del proyecto, anterior a la reescritura con React.

| Carpeta | Contenido | Sustituido por |
|---|---|---|
| `frontend/` | Páginas HTML estáticas con JavaScript y CSS propios | `frontend/` (React + Vite) |
| `database/vetstock_schema_v1.sql` | Esquema MySQL de la v1 | `database/migrations/001_esquema_inicial.sql` |
| `database/pruebas_flujo_v1.sh` | Pruebas con `curl` de la v1 | Pruebas JUnit de `backend/src/test` |

No ejecutes `vetstock_schema_v1.sql` sobre la base de datos actual: recrea un
esquema antiguo e incompatible.

Los scripts de prueba intermedios (shell y Python de la fase de Odoo) se
eliminaron porque usaban reglas que ya no existen. Siguen disponibles en el
historial de Git.
