#!/usr/bin/env bash
# ============================================================
# VetStock Manager - Arrancar el servidor Odoo de pruebas
#
# Instala (la primera vez) y arranca un Odoo 18 Community en
# http://localhost:8069 con la base de datos `vetstock_erp`,
# para poder usar la integración REAL del backend:
#     ODOO_MODO=REAL ./mvnw spring-boot:run
#
# Requisitos: PostgreSQL y Python 3 (venv). El propio script
# prepara el código fuente de Odoo y sus dependencias.
#
# Uso:   bash scripts/arrancar_odoo.sh
# ============================================================
set -e

ODOO_HOME="${ODOO_HOME:-$HOME/odoo18}"
ODOO_DB="vetstock_erp"

# 1. PostgreSQL
if ! pg_isready -q 2>/dev/null; then
    echo "Arrancando PostgreSQL..."
    sudo service postgresql start
    sleep 2
fi

# Usuario de Odoo en PostgreSQL
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='odoo'" | grep -q 1; then
    sudo -u postgres psql -c "CREATE USER odoo WITH PASSWORD 'odoo' CREATEDB;"
fi

# 2. Código fuente de Odoo 18 + entorno virtual (solo la primera vez)
if [ ! -d "$ODOO_HOME/src" ]; then
    echo "Descargando Odoo 18 (unos 190 MB, solo la primera vez)..."
    mkdir -p "$ODOO_HOME"
    cd "$ODOO_HOME"
    wget -q https://github.com/odoo/odoo/archive/refs/heads/18.0.tar.gz -O odoo.tar.gz
    tar xzf odoo.tar.gz && mv odoo-18.0 src && rm odoo.tar.gz
    python3 -m venv venv
    # psycopg2 y python-ldap se sustituyen por alternativas sin compilación nativa
    grep -v "^python-ldap" src/requirements.txt > reqs.txt
    ./venv/bin/pip install -q --upgrade pip wheel
    ./venv/bin/pip install -q -r reqs.txt
fi
cd "$ODOO_HOME"

# 3. Configuración
cat > odoo.conf <<EOF
[options]
admin_passwd = adminodoo
db_host = localhost
db_port = 5432
db_user = odoo
db_password = odoo
addons_path = src/addons,src/odoo/addons
data_dir = $ODOO_HOME/data
http_port = 8069
EOF

# 4. Inicializar la base de datos si no existe (con el módulo de ventas)
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='$ODOO_DB'" | grep -q 1; then
    echo "Inicializando la base de datos $ODOO_DB (unos minutos)..."
    ./venv/bin/python src/odoo-bin -c odoo.conf -d $ODOO_DB -i sale_management --without-demo=all --stop-after-init
fi

# 5. Arrancar el servidor
echo "Odoo arrancando en http://localhost:8069 (login admin / admin, base $ODOO_DB)"
exec ./venv/bin/python src/odoo-bin -c odoo.conf
