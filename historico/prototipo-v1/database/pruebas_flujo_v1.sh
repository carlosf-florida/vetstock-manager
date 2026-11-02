#!/bin/bash
# ------------------------------------------------------------------
# Prueba el flujo completo de VetStock Manager contra la API.
# Requisitos: la aplicación arrancada y la base de datos recién creada
# con sql/vetstock_schema.sql. Uso:  bash sql/pruebas_flujo.sh
# (En Windows se puede ejecutar con Git Bash.)
# Las contraseñas son las de PRUEBA del script SQL.
# ------------------------------------------------------------------
BASE=${1:-http://localhost:8080}
ADMIN=/tmp/vet_admin.txt; VET1=/tmp/vet_vet1.txt; VET2=/tmp/vet_vet2.txt
RESP=/tmp/vet_resp.json
ok=0; fallo=0

# llamar METODO URL FICHERO_COOKIES [JSON]  -> imprime el código HTTP
llamar() {
  if [ -n "$4" ]; then
    curl -s -o $RESP -w "%{http_code}" -X "$1" -H "Content-Type: application/json" -b "$3" -d "$4" "$BASE$2"
  else
    curl -s -o $RESP -w "%{http_code}" -X "$1" -b "$3" "$BASE$2"
  fi
}
login() { curl -s -o $RESP -w "%{http_code}" -c "$3" -d "username=$1&password=$2" "$BASE/api/auth/login"; }
comprobar() {
  if [ "$2" = "$3" ]; then echo "OK    $1 -> $3"; ok=$((ok+1));
  else echo "FALLO $1 -> esperado $2, obtenido $3"; cat $RESP; echo; fallo=$((fallo+1)); fi
}
campo() { grep -o "\"$1\":[0-9]*" $RESP | head -1 | cut -d: -f2; }   # número de un campo del JSON
stock() { llamar GET /api/productos/5 $ADMIN > /dev/null; campo "$1"; }   # producto 5 = guantes

echo "=== Seguridad"
comprobar "Sin sesión no se puede consultar pedidos" 401 "$(llamar GET /api/pedidos /dev/null)"
comprobar "Login con contraseña incorrecta"          401 "$(login admin mala /tmp/vet_x.txt)"
comprobar "Login admin"                              200 "$(login admin admin123 $ADMIN)"
comprobar "Login veterinario 1 (dr.garcia)"          200 "$(login dr.garcia vet123 $VET1)"
comprobar "Login veterinario 2 (dra.lopez)"          200 "$(login dra.lopez vet123 $VET2)"
comprobar "Veterinario no puede ver productos"       403 "$(llamar GET /api/productos $VET1)"
comprobar "Admin ve productos"                       200 "$(llamar GET /api/productos $ADMIN)"

echo "=== Validaciones al crear pedido"
comprobar "Pedido sin líneas"              400 "$(llamar POST /api/pedidos $ADMIN '{"veterinarioId":1,"lineas":[]}')"
comprobar "Cantidad negativa"              400 "$(llamar POST /api/pedidos $ADMIN '{"veterinarioId":1,"lineas":[{"productoId":5,"cantidad":-3}]}')"
comprobar "Producto inexistente"           404 "$(llamar POST /api/pedidos $ADMIN '{"veterinarioId":1,"lineas":[{"productoId":9999,"cantidad":1}]}')"
comprobar "Veterinario inexistente"        404 "$(llamar POST /api/pedidos $ADMIN '{"veterinarioId":9999,"lineas":[{"productoId":5,"cantidad":1}]}')"
comprobar "Stock insuficiente"             400 "$(llamar POST /api/pedidos $ADMIN '{"veterinarioId":1,"lineas":[{"productoId":3,"cantidad":999}]}')"
comprobar "Producto repetido en el pedido" 400 "$(llamar POST /api/pedidos $ADMIN '{"veterinarioId":1,"lineas":[{"productoId":5,"cantidad":1},{"productoId":5,"cantidad":1}]}')"

echo "=== Flujo completo: crear -> autorizar -> preparar -> enviar"
DISP0=$(stock stockDisponible); ACT0=$(stock stockActual)
comprobar "Crear pedido (guantes x2, catéteres x1)" 201 "$(llamar POST /api/pedidos $ADMIN '{"veterinarioId":1,"observaciones":"Prueba automática","lineas":[{"productoId":5,"cantidad":2},{"productoId":8,"cantidad":1}]}')"
ID=$(campo id)
echo "      (pedido creado con id $ID)"
comprobar "Se reservó el stock (disponible baja 2)" "$((DISP0-2))" "$(stock stockDisponible)"
comprobar "El stock actual no cambia aún"            "$ACT0"       "$(stock stockActual)"
comprobar "Veterinario 2 no puede ver pedido ajeno"  403 "$(llamar GET /api/pedidos/$ID $VET2)"
comprobar "Veterinario 2 no puede autorizar ajeno"   403 "$(llamar PATCH /api/pedidos/$ID/autorizar $VET2)"
comprobar "Admin no puede autorizar (solo vet)"      403 "$(llamar PATCH /api/pedidos/$ID/autorizar $ADMIN)"
comprobar "Veterinario 1 ve su pedido"               200 "$(llamar GET /api/pedidos/$ID $VET1)"
comprobar "No se puede preparar sin autorizar"       400 "$(llamar PATCH /api/pedidos/$ID/preparar $ADMIN)"
comprobar "Veterinario 1 autoriza su pedido"         200 "$(llamar PATCH /api/pedidos/$ID/autorizar $VET1)"
comprobar "No se puede autorizar dos veces"          400 "$(llamar PATCH /api/pedidos/$ID/autorizar $VET1)"
comprobar "Admin prepara el pedido"                  200 "$(llamar PATCH /api/pedidos/$ID/preparar $ADMIN)"
comprobar "Un pedido preparado no se cancela"        400 "$(llamar PATCH /api/pedidos/$ID/cancelar $ADMIN)"
comprobar "Admin envía el pedido"                    200 "$(llamar PATCH /api/pedidos/$ID/enviar $ADMIN)"
comprobar "Stock actual baja 2 tras el envío"        "$((ACT0-2))" "$(stock stockActual)"
comprobar "Disponible sigue igual tras el envío"     "$((DISP0-2))" "$(stock stockDisponible)"
comprobar "Un pedido enviado no cambia más"          400 "$(llamar PATCH /api/pedidos/$ID/preparar $ADMIN)"

echo "=== Cancelación libera el stock"
DISP1=$(stock stockDisponible)
comprobar "Crear segundo pedido (guantes x3)"        201 "$(llamar POST /api/pedidos $ADMIN '{"veterinarioId":1,"lineas":[{"productoId":5,"cantidad":3}]}')"
ID2=$(campo id)
comprobar "Reserva aplicada (disponible baja 3)"     "$((DISP1-3))" "$(stock stockDisponible)"
comprobar "Veterinario 2 no puede cancelar ajeno"    403 "$(llamar PATCH /api/pedidos/$ID2/cancelar $VET2)"
comprobar "Veterinario 1 rechaza su pedido"          200 "$(llamar PATCH /api/pedidos/$ID2/cancelar $VET1)"
comprobar "La reserva se libera"                     "$DISP1" "$(stock stockDisponible)"

echo "=== Otras consultas"
comprobar "Pedido inexistente"                       404 "$(llamar GET /api/pedidos/99999 $ADMIN)"
comprobar "Filtro de estado no válido"               400 "$(llamar GET '/api/pedidos?estado=INVENTADO' $ADMIN)"
comprobar "Dashboard del admin"                      200 "$(llamar GET /api/dashboard $ADMIN)"
comprobar "Dashboard del veterinario"                200 "$(llamar GET /api/dashboard $VET1)"
comprobar "Historial de movimientos"                 200 "$(llamar GET /api/stock/movimientos $ADMIN)"
comprobar "Salida mayor que el disponible"           400 "$(llamar POST /api/stock/salida $ADMIN '{"productoId":3,"cantidad":999,"motivo":"Prueba"}')"
comprobar "Entrada de stock"                         201 "$(llamar POST /api/stock/entrada $ADMIN '{"productoId":3,"cantidad":10,"motivo":"Prueba"}')"
comprobar "Código de producto repetido"              400 "$(llamar POST /api/productos $ADMIN '{"codigo":"MED-001","nombre":"Repetido","categoria":"MEDICAMENTO","stockMinimo":1}')"

echo
echo "RESULTADO: $ok correctas, $fallo fallidas"
[ $fallo -eq 0 ]
