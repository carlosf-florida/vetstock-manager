FROM mysql:8.4

# Solo para inicializar un volumen vacio mediante el entrypoint oficial.
# No montar archivos SQL desde el host: evita el fallo de montaje en Docker Desktop.
# Una base de datos existente sigue en el volumen mysql-data y no se reinicializa.
COPY migrations/001_esquema_inicial.sql /docker-entrypoint-initdb.d/001_esquema_inicial.sql
COPY migrations/008_vistas_powerbi.sql /docker-entrypoint-initdb.d/008_vistas_powerbi.sql
