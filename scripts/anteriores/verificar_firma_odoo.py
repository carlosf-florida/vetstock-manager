#!/usr/bin/env python3
"""Lee pedidos de Odoo de demostración y verifica un único resumen VetStock.
Uso: python3 scripts/verificar_firma_odoo.py VSM-8 VSM-9
Solo lectura. Credenciales de demostración, sustituibles por variables ODOO_*.
"""
import argparse
import json
import os
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('referencias', nargs='+')
parser.add_argument('--firmados', action='store_true')
args = parser.parse_args()
url = os.environ.get('ODOO_URL', 'http://localhost:8069').rstrip('/') + '/jsonrpc'
db = os.environ.get('ODOO_DB', 'vetstock_erp')
user = os.environ.get('ODOO_USUARIO', 'admin')
password = os.environ.get('ODOO_CLAVE', 'admin')

def rpc(service, method, values):
    data = json.dumps({'jsonrpc': '2.0', 'method': 'call', 'params': {'service': service, 'method': method, 'args': values}}, ensure_ascii=False).encode('utf-8')
    request = urllib.request.Request(url, data, {'Content-Type': 'application/json; charset=UTF-8'})
    result = json.load(urllib.request.urlopen(request, timeout=30))
    assert 'error' not in result, result.get('error')
    return result['result']

uid = rpc('common', 'login', [db, user, password])
assert uid, 'Credenciales Odoo no válidas'
rows = rpc('object', 'execute_kw', [db, uid, password, 'sale.order', 'search_read', [[['client_order_ref', 'in', args.referencias]]], {'fields': ['id', 'name', 'client_order_ref', 'state', 'note']}])
for reference in args.referencias:
    matches = [row for row in rows if row['client_order_ref'] == reference]
    assert len(matches) == 1, f'{reference}: esperaba un solo pedido, hay {len(matches)}'
    row = matches[0]
    note = row.get('note') or ''
    assert note.count('Estado de firma:') == 1, f'{reference}: resúmenes de firma duplicados'
    if args.firmados:
        assert 'Estado de firma: Firmado' in note, f'{reference}: todavía por firmar'
    print(json.dumps(row, ensure_ascii=False), flush=True)
print(f'OK: {len(args.referencias)} pedidos, un resumen actualizado por pedido.')
