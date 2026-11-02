#!/usr/bin/env python3
"""Prueba IVA4% con datos de demostración. Crea pedidos y dos productos de redondeo.
Nunca ejecutar contra producción. Uso: python3 scripts/pruebas_iva_odoo.py --base http://localhost:8081
"""
import argparse
import json
import os
import urllib.request
import uuid
from decimal import Decimal
from pruebas_firma_pago_odoo import Api, PNG

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base', default='http://localhost:8080')
args = parser.parse_args()
aux = Api(args.base).login('aux.sanroque', 'aux123')
admin = Api(args.base).login('admin', 'admin123')
checks = 0

def check(name, condition):
    global checks
    assert condition, name
    checks += 1
    print('OK  ' + name, flush=True)

def dec(value): return Decimal(str(value)).quantize(Decimal('0.01'))

def rpc(service, method, values):
    url = os.environ.get('ODOO_URL', 'http://localhost:8069').rstrip('/') + '/jsonrpc'
    data = json.dumps({'jsonrpc': '2.0', 'method': 'call', 'params': {'service': service, 'method': method, 'args': values}}).encode('utf-8')
    response = json.load(urllib.request.urlopen(urllib.request.Request(url, data, {'Content-Type': 'application/json'}), timeout=60))
    assert 'error' not in response, response
    return response['result']

db = os.environ.get('ODOO_DB', 'vetstock_erp')
user = os.environ.get('ODOO_USUARIO', 'admin')
password = os.environ.get('ODOO_CLAVE', 'admin')
uid = rpc('common', 'login', [db, user, password])
assert uid

def obj(model, method, positional, kwargs=None):
    return rpc('object', 'execute_kw', [db, uid, password, model, method, positional, kwargs or {}])

def read_odoo(id_):
    records = obj('sale.order', 'search_read', [[['client_order_ref', '=', f'VSM-{id_}']]], {'fields': ['id', 'name', 'currency_id', 'amount_untaxed', 'amount_tax', 'amount_total', 'order_line', 'note']})
    check('una sola venta Odoo para la referencia', len(records) == 1)
    return records[0]

def create(lines):
    order = aux.ok('POST', '/api/pedidos', {'lineas': lines, 'observaciones': 'Prueba IVA4% y total idéntico al ERP'})
    aux.ok('PATCH', f"/api/pedidos/{order['id']}/enviar-firma")
    return aux.ok('GET', f"/api/pedidos/{order['id']}")

def pay(order):
    start = aux.ok('POST', f"/api/pagos/{order['id']}/iniciar", {})
    check('importe iniciado incluye el IVA calculado por backend', dec(start['importe']) == dec(order['total']))
    aux.ok('POST', '/api/pagos/simulado/' + start['referencia'], {'resultado': 'CONFIRMADO'})
    order = aux.ok('GET', f"/api/pedidos/{order['id']}")
    check('pago confirmado y exportación completada', order['estadoPago'] == 'PAGADO' and order['odooId'] and not order['odooSincronizacionPendiente'])
    return order

def equal_totals(order, erp):
    check('Odoo usa EUR y no la moneda global USD', erp['currency_id'][1] == 'EUR')
    check('base de VetStock y Odoo coinciden', dec(erp['amount_untaxed']) == dec(order['baseImponible']))
    check('IVA de VetStock y Odoo coinciden', dec(erp['amount_tax']) == dec(order['importeIva']))
    check('total de VetStock y Odoo coinciden', dec(erp['amount_total']) == dec(order['total']))
    lines = obj('sale.order.line', 'read', [erp['order_line']], {'fields': ['tax_id']})
    for line in lines:
        taxes = obj('account.tax', 'read', [line['tax_id']], {'fields': ['amount', 'price_include', 'type_tax_use']})
        check('solo impuesto4%, venta y no incluido, sin acumular15%', len(taxes) == 1 and dec(taxes[0]['amount']) == Decimal('4.00') and not taxes[0]['price_include'] and taxes[0]['type_tax_use'] == 'sale')

first = create([{'productoId': 1, 'cantidad': 2}])
check('base neta 29,00', dec(first['baseImponible']) == Decimal('29.00'))
check('IVA4% 1,16 y total30,16', dec(first['importeIva']) == Decimal('1.16') and dec(first['total']) == Decimal('30.16'))
first = pay(first)
erp = read_odoo(first['id'])
equal_totals(first, erp)
check('Odoo refleja pago con firma aún pendiente', 'Estado de firma: Por firmar' in erp['note'])
old_erp_id = erp['id']
aux.ok('PATCH', f"/api/pedidos/{first['id']}/firmar", {'tipoFirma': 'DIBUJO', 'imagenBase64': PNG, 'confirmacion': True})
first = aux.ok('GET', f"/api/pedidos/{first['id']}")
erp = read_odoo(first['id'])
check('firma posterior conserva id y estado fiscal', erp['id'] == old_erp_id and dec(erp['amount_total']) == Decimal('30.16') and 'Estado de firma: Firmado' in erp['note'])
audit = aux.ok('GET', f"/api/pedidos/{first['id']}/firma")
snapshot = json.loads(audit['contenidoPedido'])
check('firma inmutable conserva base,IVA,total y moneda', dec(snapshot['baseImponible']) == Decimal('29.00') and dec(snapshot['importeIva']) == Decimal('1.16') and dec(snapshot['total']) == Decimal('30.16') and snapshot['moneda'] == 'EUR')

codes = []
ids = []
for letter in ('A', 'B'):
    code = 'IVA-TEST-' + letter + '-' + uuid.uuid4().hex[:8]
    product = admin.ok('POST', '/api/productos', {'codigo': code, 'nombre': 'Prueba redondeo IVA ' + letter, 'categoria': 'MATERIAL_CLINICO', 'precio': 0.13, 'stockInicial': 20, 'stockMinimo': 0})
    codes.append(code)
    ids.append(product['id'])
small = create([{'productoId': id_, 'cantidad': 1} for id_ in ids])
check('redondeo por línea conserva IVA0,02, no0,01', dec(small['baseImponible']) == Decimal('0.26') and dec(small['importeIva']) == Decimal('0.02') and dec(small['total']) == Decimal('0.28'))
small = pay(small)
equal_totals(small, read_odoo(small['id']))
print(f'RESULTADO: {checks} comprobaciones IVA/Odoo correctas. Pedidos {first["id"]} y {small["id"]}.', flush=True)
