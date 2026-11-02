#!/usr/bin/env python3
"""Demostración: crea y paga un pedido con ERP no disponible; reintenta sin cobrar otra vez.
Requiere dos backends de PRUEBAS con la misma MySQL: --fallo conecta con Odoo no disponible,
--real conecta con Odoo operativo. Nunca ejecutar contra producción.
"""
import argparse
from pruebas_firma_pago_odoo import Api, PNG

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--fallo', default='http://localhost:8082')
parser.add_argument('--real', default='http://localhost:8081')
args = parser.parse_args()
aux = Api(args.fallo).login('aux.sanroque', 'aux123')
admin = Api(args.real).login('admin', 'admin123')
vet = Api(args.real).login('dr.garcia', 'vet123')
order = aux.ok('POST', '/api/pedidos', {'lineas': [{'productoId': 1, 'cantidad': 1}], 'observaciones': 'Prueba de reintento con ERP no disponible (pago simulado)'})
id_ = order['id']
aux.ok('PATCH', f'/api/pedidos/{id_}/enviar-firma')
start = aux.ok('POST', f'/api/pagos/{id_}/iniciar', {})
aux.ok('POST', '/api/pagos/simulado/' + start['referencia'], {'resultado': 'CONFIRMADO'})
order = aux.ok('GET', f'/api/pedidos/{id_}')
assert order['estadoPago'] == 'PAGADO' and order['estadoFirma'] == 'PENDIENTE'
assert order['odooSincronizacionPendiente'] and order['odooUltimoError']
paid_at = order['fechaPago']
print('OK  ERP no disponible: pago conservado, firma pendiente y fallo registrado.')
result = admin.ok('POST', f'/api/odoo/pedidos/{id_}/exportar', {})
assert result['exito'], result
order = vet.ok('GET', f'/api/pedidos/{id_}')
assert order['fechaPago'] == paid_at and not order['odooSincronizacionPendiente'] and order['odooId']
odoo_id = order['odooId']
print(f'OK  Reintento sin cobrar otra vez. Referencia VSM-{id_}, Odoo ID {odoo_id}.')
vet.ok('PATCH', f'/api/pedidos/{id_}/firmar', {'tipoFirma': 'DIBUJO', 'imagenBase64': PNG, 'confirmacion': True})
order = vet.ok('GET', f'/api/pedidos/{id_}')
assert order['estado'] == 'LISTO_PREPARAR' and order['odooId'] == odoo_id and not order['odooSincronizacionPendiente']
print('OK  Firma posterior actualiza el mismo pedido recuperado.')
print(f'RESULTADO: 3 comprobaciones de recuperación OK. Pedido {id_}.')
