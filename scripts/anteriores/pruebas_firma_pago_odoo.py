#!/usr/bin/env python3
"""Prueba de integración: crea dos pedidos de demostración y pagos SIMULADOS.
Uso: python3 scripts/pruebas_firma_pago_odoo.py --base http://localhost:8081
No ejecutar contra una instalación de producción. Conserva los pedidos de prueba.
Requiere catálogo de prueba y Odoo conectado si se usa --odoo-real.
"""
import argparse
import http.cookiejar
import json
import urllib.error
import urllib.parse
import urllib.request

PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

class Api:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, method, path, payload=None, form=False):
        data = None
        headers = {}
        if payload is not None:
            data = (urllib.parse.urlencode(payload) if form else json.dumps(payload, ensure_ascii=False)).encode('utf-8')
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json; charset=UTF-8'
        req = urllib.request.Request(self.base + path, data, headers, method=method)
        try:
            with self.opener.open(req, timeout=90) as response:
                body = response.read()
                return response.status, json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            body = error.read()
            try:
                result = json.loads(body)
            except (ValueError, UnicodeError):
                result = {'mensaje': body.decode('utf-8', errors='replace')}
            return error.code, result

    def ok(self, method, path, payload=None, form=False):
        status, result = self.request(method, path, payload, form)
        assert 200 <= status < 300, f'{method} {path}: HTTP {status}: {result}'
        return result

    def login(self, user, password):
        self.ok('POST', '/api/auth/login', {'username': user, 'password': password}, form=True)
        return self


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://localhost:8080')
    parser.add_argument('--odoo-real', action='store_true', help='Exigir ID del pedido y ausencia de fallo de sincronización')
    args = parser.parse_args()
    aux = Api(args.base).login('aux.sanroque', 'aux123')
    vet = Api(args.base).login('dr.garcia', 'vet123')
    other = Api(args.base).login('dr.martin', 'vet123')
    admin = Api(args.base).login('admin', 'admin123')
    checks = 0

    def check(description, condition):
        nonlocal checks
        assert condition, description
        checks += 1
        print('OK  ' + description, flush=True)

    def create():
        order = aux.ok('POST', '/api/pedidos', {'lineas': [{'productoId': 1, 'cantidad': 1}], 'observaciones': 'Prueba firma/pago independientes (simulado)'})
        check('responsable automático, sin seleccionar veterinario', order['veterinarioUsername'] == 'dr.garcia')
        return aux.ok('PATCH', f"/api/pedidos/{order['id']}/enviar-firma")

    def pay(order):
        start = aux.ok('POST', f"/api/pagos/{order['id']}/iniciar", {})
        aux.ok('POST', '/api/pagos/simulado/' + start['referencia'], {'resultado': 'CONFIRMADO'})
        return aux.ok('GET', f"/api/pedidos/{order['id']}")

    def signature(kind='DIBUJO', code=None):
        result = {'tipoFirma': kind, 'imagenBase64': PNG, 'confirmacion': True}
        if kind == 'IMAGEN': result['nombreArchivo'] = 'firma-propia.png'
        if code is not None: result['numeroColegiado'] = code
        return result

    first = create()
    first_id = first['id']
    code, _ = other.request('PATCH', f'/api/pedidos/{first_id}/firmar', signature())
    check('otra clínica no puede firmar', code == 403)
    code, _ = aux.request('PATCH', f'/api/pedidos/{first_id}/firmar', signature(code='NO-COINCIDE'))
    check('colegiado incorrecto no sirve como confirmación', code == 400)
    code, _ = aux.request('PATCH', f'/api/pedidos/{first_id}/firmar', {'tipoFirma': 'IMAGEN', 'imagenBase64': '', 'confirmacion': True, 'numeroColegiado': first['numeroColegiadoResponsable']})
    check('el número solo no sustituye una firma', code == 400)
    first = pay(first)
    check('pago confirmado antes de firmar', first['estadoPago'] == 'PAGADO')
    check('firma sigue pendiente y no se habilita preparación', first['estadoFirma'] == 'PENDIENTE' and first['estado'] == 'PENDIENTE_FIRMA')
    if args.odoo_real:
        check('el pedido pagado ya existe en Odoo, sin esperar expedición', first['odooId'] is not None and not first['odooSincronizacionPendiente'])
    odoo_id = first['odooId']
    code, _ = admin.request('PATCH', f'/api/pedidos/{first_id}/iniciar-preparacion')
    check('el almacén no prepara mientras falta la firma', code == 400)
    aux.ok('PATCH', f'/api/pedidos/{first_id}/firmar', signature())
    first = aux.ok('GET', f'/api/pedidos/{first_id}')
    check('el auxiliar queda identificado como firmante real', first['firmanteRol'] == 'AUXILIAR' and first['firmanteNombre'])
    check('firmar después de pagar habilita preparación', first['estado'] == 'LISTO_PREPARAR')
    if args.odoo_real:
        check('la firma posterior conserva el mismo ID de Odoo', first['odooId'] == odoo_id and not first['odooSincronizacionPendiente'])
    audit = aux.ok('GET', f'/api/pedidos/{first_id}/firma')
    snapshot = json.loads(audit['contenidoPedido'])
    check('la copia inmutable distingue auxiliar y responsable', snapshot['firmanteRol'] == 'AUXILIAR' and snapshot['veterinarioResponsable'] == 'Dr. Antonio García')
    code, _ = aux.request('PATCH', f'/api/pedidos/{first_id}/firmar', signature())
    check('no se firma dos veces', code == 400)
    code, _ = aux.request('POST', f'/api/pagos/{first_id}/iniciar', {})
    check('no se paga dos veces', code == 400)
    for action in ['iniciar-preparacion', 'preparar', 'expedir']:
        admin.ok('PATCH', f'/api/pedidos/{first_id}/{action}')
    check('expedición permitida después de firma y pago', aux.ok('GET', f'/api/pedidos/{first_id}')['estado'] == 'EXPEDIDO')

    second = create()
    second_id = second['id']
    vet.ok('PATCH', f'/api/pedidos/{second_id}/firmar', signature('IMAGEN', second['numeroColegiadoResponsable']))
    second = vet.ok('GET', f'/api/pedidos/{second_id}')
    check('firma por imagen y colegiado opcional identificativo', second['estadoFirma'] == 'FIRMADO' and second['firmanteRol'] == 'VETERINARIO' and second['estadoPago'] == 'PENDIENTE')
    second = pay(second)
    check('también funciona firmar primero y pagar después', second['estado'] == 'LISTO_PREPARAR')
    if args.odoo_real:
        check('segundo pedido sincronizado automáticamente', second['odooId'] is not None and not second['odooSincronizacionPendiente'])
    print(f'RESULTADO: {checks} comprobaciones OK. Pedidos de prueba: {first_id}, {second_id}.', flush=True)

if __name__ == '__main__':
    main()
