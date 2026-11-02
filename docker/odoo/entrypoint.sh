#!/usr/bin/env bash
set -e

DB_NAME="${ODOO_DB_NAME:-vetstock_erp}"
PG_HOST="${HOST:-db-odoo}"
PG_USER="${USER:-odoo}"
PG_PASSWORD="${PASSWORD:-odoo}"

# Parámetros explícitos: no dependemos del entrypoint interno de la imagen.
ODOO_ARGS=(--db_host "$PG_HOST" --db_port 5432 --db_user "$PG_USER" --db_password "$PG_PASSWORD")

# La imagen oficial de Odoo ya incluye Python y psycopg2. Esperamos a
# PostgreSQL sin depender de que tenga instalado el cliente psql.
until PGHOST="$PG_HOST" PGUSER="$PG_USER" PGPASSWORD="$PG_PASSWORD" python3 - <<'PY'
import os, psycopg2
try:
    conn = psycopg2.connect(host=os.environ['PGHOST'], user=os.environ['PGUSER'], password=os.environ['PGPASSWORD'], dbname='postgres')
    conn.close()
except Exception:
    raise SystemExit(1)
PY
do
  echo "Esperando a PostgreSQL..."
  sleep 2
done

# La base se crea una sola vez; el volumen de PostgreSQL conserva Odoo entre reinicios.
if ! PGHOST="$PG_HOST" PGUSER="$PG_USER" PGPASSWORD="$PG_PASSWORD" python3 - "$DB_NAME" <<'PY'
import os, sys, psycopg2
name = sys.argv[1]
conn = psycopg2.connect(host=os.environ['PGHOST'], user=os.environ['PGUSER'], password=os.environ['PGPASSWORD'], dbname='postgres')
cur = conn.cursor()
cur.execute('SELECT 1 FROM pg_database WHERE datname = %s', (name,))
found = cur.fetchone() is not None
cur.close(); conn.close()
sys.exit(0 if found else 1)
PY
then
  echo "Inicializando Odoo en la base $DB_NAME..."
  odoo "${ODOO_ARGS[@]}" -d "$DB_NAME" -i web,sale_management --without-demo=all --stop-after-init
fi

echo "Odoo disponible en http://localhost:8069 (base $DB_NAME)"
exec odoo "${ODOO_ARGS[@]}" --database "$DB_NAME" --http-port 8069
