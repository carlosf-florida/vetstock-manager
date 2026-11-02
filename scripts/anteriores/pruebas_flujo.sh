#!/usr/bin/env bash
# ============================================================
# VetStock Manager - Pruebas de flujo (recorrido completo)
#
# Requisitos: el backend arrancado en localhost:8080 y la base de
# datos recién creada con database/migrations/001_esquema_inicial.sql
#
# Uso:   bash scripts/pruebas_flujo.sh
# ============================================================

B=http://localhost:8080
TMP=$(mktemp -d)
OK=0; FALLO=0

# ---------- utilidades ----------

comprobar() { # comprobar <descripción> <esperado> <obtenido>
    if [ "$2" = "$3" ]; then
        echo "OK    $1"; OK=$((OK+1))
    else
        echo "FALLO $1 (esperado $2, obtenido $3)"; FALLO=$((FALLO+1))
    fi
}

login() { # login <archivo_jar> <usuario> <clave>
    curl -s -c "$1" -X POST $B/api/auth/login -d "username=$2&password=$3" > /dev/null
}

estado_de() { # imprime el estado de un pedido
    python3 -c "import json; print(json.load(open('$1'))['estado'])" 2>/dev/null
}

# Una imagen PNG 1x1 válida, en base64
PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

# ---------- sesiones ----------

login $TMP/admin.jar   admin        admin123
login $TMP/vet1.jar    dr.garcia    vet123
login $TMP/vet2.jar    dr.martin    vet123
login $TMP/lopez.jar   dra.lopez    vet123
login $TMP/aux.jar     aux.sanroque aux123

# ---------- 1. Sesiones y roles ----------

ME=$(curl -s -b $TMP/aux.jar $B/api/auth/me)
comprobar "Login del auxiliar devuelve rol AUXILIAR" "AUXILIAR" "$(echo "$ME" | python3 -c "import json,sys; print(json.load(sys.stdin)['rol'])")"
comprobar "Sin sesión la API responde 401" "401" "$(curl -s -o /dev/null -w '%{http_code}' $B/api/pedidos)"
comprobar "Contraseña incorrecta responde 401" "401" "$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/auth/login -d 'username=admin&password=no')"
curl -s -b $TMP/aux.jar $B/api/informes/pendientes -o /dev/null -w '' -w "%{http_code}" > $TMP/cod
comprobar "El auxiliar no accede a informes (403)" "403" "$(cat $TMP/cod)"

# ---------- 2. Auxiliar prepara un pedido; primero debe confirmarlo ----------

curl -s -b $TMP/aux.jar -X POST $B/api/pedidos -H "Content-Type: application/json" \
     -d '{"veterinarioId":1,"lineas":[{"productoId":1,"cantidad":5}]}' -o $TMP/p.json
comprobar "Auxiliar crea pedido BORRADOR (201)" "201" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/aux.jar -X POST $B/api/pedidos -H 'Content-Type: application/json' -d '{"veterinarioId":1,"lineas":[{"productoId":1,"cantidad":5}]}')"
ID=$(python3 -c "import json; print(json.load(open('$TMP/p.json'))['id'])")
R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/aux.jar -X PATCH $B/api/pedidos/$ID/firmar -H 'Content-Type: application/json' -d '{"tipoFirma":"DIBUJO","imagenBase64":"x","confirmacion":true}')
comprobar "No se firma un pedido todavía BORRADOR (400)" "400" "$R"
R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar -X PATCH $B/api/pedidos/$ID/iniciar-preparacion)
comprobar "Pedido BORRADOR no se puede preparar (400)" "400" "$R"

# ---------- 3. Reserva de stock ----------

RESERVA1=$(mysql -uroot -proot -N -e "USE vetstock; SELECT stock_reservado FROM productos WHERE id=1;" 2>/dev/null | tr -d ' ')
curl -s -b $TMP/aux.jar -X PATCH $B/api/pedidos/$ID/enviar-firma -o $TMP/p.json
RESERVA2=$(mysql -uroot -proot -N -e "USE vetstock; SELECT stock_reservado FROM productos WHERE id=1;" 2>/dev/null | tr -d ' ')
comprobar "Enviar a firma reserva stock (+5)" "$((RESERVA1+5))" "$RESERVA2"
comprobar "Estado PENDIENTE_FIRMA" "PENDIENTE_FIRMA" "$(estado_de $TMP/p.json)"

# ---------- 4. Solo el veterinario asignado firma ----------

R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/vet2.jar -X PATCH $B/api/pedidos/$ID/firmar -H 'Content-Type: application/json' -d "{\"tipoFirma\":\"DIBUJO\",\"imagenBase64\":\"$PNG\",\"confirmacion\":true}")
comprobar "Un veterinario ajeno NO puede firmar (403)" "403" "$R"
curl -s -b $TMP/vet1.jar -X PATCH $B/api/pedidos/$ID/firmar -H 'Content-Type: application/json' \
     -d "{\"tipoFirma\":\"DIBUJO\",\"imagenBase64\":\"$PNG\",\"confirmacion\":true}" -o $TMP/p.json
comprobar "El veterinario asignado firma (FIRMADO)" "FIRMADO" "$(estado_de $TMP/p.json)"
R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/vet1.jar -X PATCH $B/api/pedidos/$ID/firmar -H 'Content-Type: application/json' -d "{\"tipoFirma\":\"DIBUJO\",\"imagenBase64\":\"$PNG\",\"confirmacion\":true}")
comprobar "No se puede firmar dos veces (400)" "400" "$R"
R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar -X PATCH $B/api/pedidos/$ID/iniciar-preparacion)
comprobar "Firmado pero SIN pagar no se prepara (400)" "400" "$R"

# ---------- 5. Copia inmutable de la firma ----------

curl -s -b $TMP/admin.jar $B/api/pedidos/$ID/firma -o $TMP/f.json
comprobar "La firma guarda al veterinario" "Dr. Antonio García" "$(python3 -c "import json; print(json.load(open('$TMP/f.json'))['veterinarioNombre'])")"
comprobar "La firma guarda una copia del contenido" "Sí" "$(python3 -c "import json; d=json.load(open('$TMP/f.json')); print('Sí' if 'contenidoPedido' in d and d['contenidoPedido'] else 'No')")"

# ---------- 6. Pago: fallido, reintento, confirmación e idempotencia ----------

INICIADO=$(curl -s -b $TMP/aux.jar -X POST $B/api/pagos/$ID/iniciar -o $TMP/pg.json -w '%{http_code}')
comprobar "Iniciar pago (201)" "201" "$INICIADO"
REF=$(python3 -c "import json; print(json.load(open('$TMP/pg.json'))['referencia'])")
curl -s -X POST $B/api/pagos/simulado/$REF -H 'Content-Type: application/json' -d '{"resultado":"FALLADO"}' -o $TMP/nf.json
comprobar "Pago fallido -> FALLADO" "FALLADO" "$(estado_de $TMP/nf.json)"
curl -s -b $TMP/aux.jar $B/api/pedidos/$ID -o $TMP/p.json
comprobar "Tras un fallo el pedido sigue FIRMADO" "FIRMADO" "$(estado_de $TMP/p.json)"
curl -s -b $TMP/aux.jar -X POST $B/api/pagos/$ID/iniciar -o $TMP/pg2.json
REF2=$(python3 -c "import json; print(json.load(open('$TMP/pg2.json'))['referencia'])")
comprobar "Reintento genera referencia nueva" "Sí" "$([ "$REF" != "$REF2" ] && echo Sí || echo No)"
TOTAL=$(python3 -c "import json; print(json.load(open('$TMP/p.json'))['total'])")
curl -s -X POST $B/api/pagos/notificacion -H 'Content-Type: application/json' -H 'X-Notificacion-Token: token-simulado-demo' \
     -d "{\"referencia\":\"$REF2\",\"importe\":$TOTAL,\"moneda\":\"EUR\",\"resultado\":\"CONFIRMADO\"}" -o $TMP/nc.json
comprobar "Notificación correcta -> CONFIRMADO" "CONFIRMADO" "$(estado_de $TMP/nc.json)"
curl -s -b $TMP/aux.jar $B/api/pedidos/$ID -o $TMP/p.json
comprobar "Pedido pagado -> LISTO_PREPARAR" "LISTO_PREPARAR" "$(estado_de $TMP/p.json)"
curl -s -X POST $B/api/pagos/notificacion -H 'Content-Type: application/json' -H 'X-Notificacion-Token: token-simulado-demo' \
     -d "{\"referencia\":\"$REF2\",\"importe\":$TOTAL,\"moneda\":\"EUR\",\"resultado\":\"CONFIRMADO\"}" -o $TMP/nr.json
comprobar "Notificación duplicada no rompe nada (200)" "200" "$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/pagos/notificacion -H 'Content-Type: application/json' -H 'X-Notificacion-Token: token-simulado-demo' -d "{\"referencia\":\"$REF2\",\"importe\":$TOTAL,\"moneda\":\"EUR\",\"resultado\":\"CONFIRMADO\"}")"
R=$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/pagos/notificacion -H 'Content-Type: application/json' -H 'X-Notificacion-Token: malo' -d '{"referencia":"X","importe":1,"moneda":"EUR","resultado":"CONFIRMADO"}')
comprobar "Token de notificación incorrecto (403)" "403" "$R"

# ---------- 7. Preparación y expedición ----------

curl -s -b $TMP/admin.jar -X PATCH $B/api/pedidos/$ID/iniciar-preparacion -o $TMP/p.json
comprobar "Pagado y firmado -> EN_PREPARACION" "EN_PREPARACION" "$(estado_de $TMP/p.json)"
curl -s -b $TMP/admin.jar -X PATCH $B/api/pedidos/$ID/preparar -o $TMP/p.json
comprobar "EN_PREPARACION -> PREPARADO" "PREPARADO" "$(estado_de $TMP/p.json)"
ACTUAL1=$(mysql -uroot -proot -N -e "USE vetstock; SELECT stock_actual FROM productos WHERE id=1;" 2>/dev/null | tr -d ' ')
curl -s -b $TMP/admin.jar -X PATCH $B/api/pedidos/$ID/expedir -o $TMP/p.json
ACTUAL2=$(mysql -uroot -proot -N -e "USE vetstock; SELECT stock_actual FROM productos WHERE id=1;" 2>/dev/null | tr -d ' ')
comprobar "Expedir descuenta el stock físico (-5)" "$((ACTUAL1-5))" "$ACTUAL2"
RESERVA3=$(mysql -uroot -proot -N -e "USE vetstock; SELECT stock_reservado FROM productos WHERE id=1;" 2>/dev/null | tr -d ' ')
comprobar "Expedir libera la reserva (0 extra)" "$RESERVA3" "$RESERVA3"
R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar -X PATCH $B/api/pedidos/$ID/expedir)
comprobar "No se puede expedir dos veces (400)" "400" "$R"

# ---------- 8. Stock insuficiente y permisos entre clínicas ----------

curl -s -b $TMP/aux.jar -X POST $B/api/pedidos -H 'Content-Type: application/json' \
     -d '{"veterinarioId":1,"lineas":[{"productoId":3,"cantidad":9999}]}' -o $TMP/p9.json
P9=$(python3 -c "import json; print(json.load(open('$TMP/p9.json'))['id'])")
R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/aux.jar -X PATCH $B/api/pedidos/$P9/enviar-firma)
comprobar "Stock insuficiente no permite enviar a firma (400)" "400" "$R"
R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/vet2.jar $B/api/pedidos/$ID)
comprobar "Otra clínica no ve el pedido (403)" "403" "$R"
R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/vet2.jar $B/api/pedidos/$ID/firma/imagen)
comprobar "Otra clínica no ve la firma (403)" "403" "$R"

# ---------- 9. Bultos, palets y destinos ----------

BULTO_COD=$(curl -s -w '%{http_code}' -b $TMP/admin.jar -X POST $B/api/bultos -H 'Content-Type: application/json' -d "{\"identificador\":\"BULTO-TEST-1\",\"pedidoId\":$ID}" -o $TMP/b.json)
comprobar "Registrar bulto (201)" "201" "$BULTO_COD"
PALET_COD=$(curl -s -w '%{http_code}' -b $TMP/admin.jar -X POST $B/api/palets -H 'Content-Type: application/json' -d '{"identificador":"PALET-TEST-1","destinoId":1}' -o $TMP/pal.json)
comprobar "Crear palet con destino (201)" "201" "$PALET_COD"
BULTO=$(python3 -c "import json; print(json.load(open('$TMP/b.json'))['id'])")
PALET=$(python3 -c "import json; print(json.load(open('$TMP/pal.json'))['id'])")
curl -s -b $TMP/admin.jar -X PATCH $B/api/bultos/$BULTO/asignar-palet -H 'Content-Type: application/json' -d "{\"paletId\":$PALET}" -o $TMP/b.json
comprobar "Asignar bulto a palet" "$PALET" "$(python3 -c "import json; print(json.load(open('$TMP/b.json'))['paletId'])")"
curl -s -b $TMP/admin.jar $B/api/palets/$PALET -o $TMP/pal.json
comprobar "El palet muestra sus bultos" "1" "$(python3 -c "import json; print(len(json.load(open('$TMP/pal.json'))['bultos']))")"

# ---------- 10. Documentos ----------

printf '%%PDF-1.4 documento de prueba' > $TMP/doc.pdf
printf 'esto no es un pdf' > $TMP/falso.txt
DOC_COD=$(curl -s -b $TMP/admin.jar -X POST $B/api/pedidos/$ID/documentos -F "archivo=@$TMP/doc.pdf" -F "tipo=PEDIDO" -o $TMP/d.json -w '%{http_code}')
DOC=$(python3 -c "import json; print(json.load(open('$TMP/d.json'))['id'])")
comprobar "Subir PDF como ADMIN (201)" "201" "$DOC_COD"
comprobar "El cliente de la clínica descarga el documento" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/aux.jar $B/api/documentos/$DOC/descargar)"
comprobar "Otra clínica NO descarga el documento (403)" "403" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/vet2.jar $B/api/documentos/$DOC/descargar)"
R=$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar -X POST $B/api/pedidos/$ID/documentos -F "archivo=@$TMP/falso.txt" -F "tipo=PEDIDO")
comprobar "Un archivo que no es PDF se rechaza (400)" "400" "$R"

# ---------- 11. Informes y dashboard ----------

comprobar "Informe productos-top (200)" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar $B/api/informes/productos-top)"
comprobar "Informe tiempos (200)" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar $B/api/informes/tiempos)"
comprobar "Informe destinos (200)" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar $B/api/informes/destinos)"
comprobar "Dashboard del admin (200)" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar $B/api/dashboard)"
comprobar "Dashboard del cliente (200)" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/aux.jar $B/api/dashboard)"

# ---------- 12. Integración con Odoo (ERP) ----------
# Funciona con cualquiera de los dos adaptadores: SIMULADO (por defecto,
# sin servidor) o REAL (vetstock.odoo.modo=REAL con un servidor Odoo).

comprobar "El auxiliar no accede a Odoo (403)" "403" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/aux.jar $B/api/odoo/estado)"
ESTADO=$(curl -s -b $TMP/admin.jar $B/api/odoo/estado)
comprobar "Estado de la integración (200)" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar $B/api/odoo/estado)"
EXPORT=$(curl -s -b $TMP/admin.jar -X POST $B/api/odoo/pedidos/$ID/exportar)
if echo "$EXPORT" | grep -q '"exito":false'; then
    comprobar "Exportar pedido a Odoo" "Sí" "$(echo "$EXPORT" | python3 -c "import json,sys; print('Sí' if json.load(sys.stdin).get('exito') else 'No')")"
else
    comprobar "Exportar pedido a Odoo (odooId asignado)" "Sí" "$(echo "$EXPORT" | python3 -c "import json,sys; d=json.load(sys.stdin); print('Sí' if d.get('odooId') else 'No')")"
fi
ESTADO2=$(curl -s -b $TMP/admin.jar $B/api/odoo/estado)
comprobar "Tras exportar hay productos sincronizados" "Sí" "$(python3 -c "import json; d=json.loads('''$ESTADO2'''); print('Sí' if d['productoSincronizados'] > 0 else 'No')")"
comprobar "Reexportar actualiza el mismo pedido (200)" "200" "$(curl -s -o /dev/null -w '%{http_code}' -b $TMP/admin.jar -X POST $B/api/odoo/pedidos/$ID/exportar)"
LOG=$(curl -s -b $TMP/admin.jar $B/api/odoo/log)
comprobar "El log de Odoo registra la operación" "Sí" "$(python3 -c "import json; d=json.loads('''$LOG'''); print('Sí' if len(d) > 0 else 'No')")"

# ---------- resultado ----------

echo ""
echo "RESULTADO: $OK correctas, $FALLO fallidas"
rm -rf $TMP
[ "$FALLO" = "0" ]
