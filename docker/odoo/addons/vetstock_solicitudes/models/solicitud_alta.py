"""Solicitud de alta en Odoo: aquí se revisa; el alta real la hace siempre el backend de VetStock.

Los botones no crean clínicas ni contactos: envían la decisión al backend en una llamada firmada
(HMAC-SHA256 del cuerpo) y guardan lo que este responde. El secreto y la URL vienen de variables
de entorno del servidor, nunca de la base de datos ni del navegador.
"""
import base64
import hashlib
import hmac
import json
import logging
import os
import time

import requests

from odoo import _, fields, models
from odoo.exceptions import AccessError, UserError

_logger = logging.getLogger(__name__)

GRUPO_REVISOR = "vetstock_solicitudes.grupo_revisor"
GRUPO_INTEGRACION = "vetstock_solicitudes.grupo_integracion"
RUTA_DECISION = "/api/integracion/odoo/solicitudes/decision"
CAMPOS_DEL_REVISOR = {"observaciones_internas"}

ESTADOS_REVISION = [
    ("pendiente", "Pendiente de revisión"),
    ("requiere_informacion", "Requiere información"),
    ("aprobada", "Aprobada"),
    ("rechazada", "Rechazada"),
]
ESTADOS_ALTA = [
    ("no_iniciada", "No iniciada"),
    ("en_proceso", "En proceso"),
    ("requiere_revision", "Requiere revisión (posible duplicado)"),
    ("fallida", "Fallida, pendiente de reintento"),
    ("completada", "Completada"),
]


class SolicitudAlta(models.Model):
    _name = "vetstock.solicitud.alta"
    _description = "Solicitud de alta de clínica"
    _inherit = ["mail.thread"]
    _rec_name = "referencia"
    _order = "fecha_solicitud desc, id desc"

    _sql_constraints = [
        ("referencia_unica", "unique(referencia)", "Ya existe una solicitud con esa referencia."),
    ]

    referencia = fields.Char(required=True, readonly=True, index=True)
    fecha_solicitud = fields.Datetime(string="Fecha", readonly=True)

    clinica_nombre = fields.Char(string="Clínica", required=True, readonly=True)
    razon_social = fields.Char(string="Razón social", readonly=True)
    identificador_fiscal = fields.Char(string="Identificador fiscal", readonly=True)
    email = fields.Char(readonly=True)
    telefono = fields.Char(string="Teléfono", readonly=True)
    direccion = fields.Char(string="Dirección", readonly=True)
    codigo_postal = fields.Char(string="Código postal", readonly=True)
    localidad = fields.Char(readonly=True)
    provincia = fields.Char(string="Provincia o región", readonly=True)
    pais = fields.Char(string="País", readonly=True)

    vet_nombre = fields.Char(string="Veterinario propuesto", readonly=True)
    vet_colegiado = fields.Char(string="Número de colegiado", readonly=True)
    vet_colegio = fields.Char(string="Colegio o provincia", readonly=True)
    vet_email = fields.Char(string="Email profesional", readonly=True)
    vet_telefono = fields.Char(string="Teléfono del veterinario", readonly=True)
    observaciones = fields.Text(string="Observaciones de la clínica", readonly=True)

    estado_revision = fields.Selection(ESTADOS_REVISION, string="Revisión", default="pendiente",
                                       required=True, readonly=True, tracking=True)
    revisado_por = fields.Char(string="Revisado por", readonly=True)
    fecha_revision = fields.Datetime(string="Fecha de revisión", readonly=True)
    observaciones_internas = fields.Text(string="Observaciones internas")

    estado_alta = fields.Selection(ESTADOS_ALTA, string="Integración", default="no_iniciada",
                                   required=True, readonly=True, tracking=True)
    mensaje_alta = fields.Char(string="Detalle de la integración", readonly=True)
    clinica_vetstock_id = fields.Integer(string="Id de la clínica en VetStock", readonly=True)
    veterinario_vetstock_id = fields.Integer(string="Id del veterinario en VetStock", readonly=True)
    partner_id = fields.Many2one("res.partner", string="Contacto en Odoo", readonly=True)

    def write(self, vals):
        # Los datos y los estados vienen de VetStock: un revisor solo escribe sus observaciones
        if (not self.env.su and not self.env.user.has_group(GRUPO_INTEGRACION)
                and set(vals) - CAMPOS_DEL_REVISOR):
            raise AccessError(_("Solo puedes modificar las observaciones internas."))
        return super().write(vals)

    def _comprobar_revisor(self):
        if not self.env.user.has_group(GRUPO_REVISOR):
            raise AccessError(_("No tienes permiso para revisar solicitudes de alta."))

    def _enviar_decision(self, decision):
        """Llama al backend. Si no responde, no se cambia nada y se avisa a quien revisa."""
        self.ensure_one()
        self._comprobar_revisor()
        url = os.environ.get("VETSTOCK_BACKEND_URL")
        secreto = os.environ.get("ODOO_SECRETO_SOLICITUDES")
        if not url or not secreto:
            raise UserError(_("Falta configurar la conexión con VetStock en el servidor de Odoo."))
        # dumps escapa los acentos: el cuerpo firmado y el recibido son exactamente los mismos bytes
        cuerpo = json.dumps({
            "referencia": self.referencia,
            "decision": decision,
            "revisor": self.env.user.name,
            "observaciones": self.observaciones_internas or "",
            "marcaTiempo": int(time.time()),
        })
        firma = base64.b64encode(hmac.new(secreto.encode(), cuerpo.encode(), hashlib.sha256).digest()).decode()
        try:
            respuesta = requests.post(url.rstrip("/") + RUTA_DECISION, data=cuerpo.encode(), timeout=60, headers={
                "Content-Type": "application/json",
                "X-Firma-Odoo": firma,
            })
        except requests.RequestException as error:
            _logger.warning("VetStock no responde al enviar la decisión de %s: %s", self.referencia, type(error).__name__)
            raise UserError(_("VetStock no responde. La decisión no se ha registrado: inténtalo más tarde.")) from error
        if respuesta.status_code != 200:
            try:
                mensaje = respuesta.json().get("mensaje")
            except ValueError:
                mensaje = None
            raise UserError(mensaje or _("VetStock ha rechazado la decisión (error %s).") % respuesta.status_code)
        self._guardar_respuesta(respuesta.json())

    def _guardar_respuesta(self, datos):
        estado_revision = datos["estadoRevision"].lower()
        valores = {
            "estado_revision": estado_revision,
            "estado_alta": datos["estadoAlta"].lower(),
            "mensaje_alta": datos.get("mensajeAlta") or False,
            "clinica_vetstock_id": datos.get("clinicaId") or 0,
            "veterinario_vetstock_id": datos.get("veterinarioId") or 0,
            "revisado_por": datos.get("revisadoPor") or False,
        }
        # Repetir una aprobación conserva la fecha de la primera
        if self.estado_revision != estado_revision or not self.fecha_revision:
            valores["fecha_revision"] = fields.Datetime.now()
        self.sudo().write(valores)

    def action_aprobar(self):
        self._enviar_decision("APROBAR")

    def action_rechazar(self):
        self._enviar_decision("RECHAZAR")

    def action_requiere_informacion(self):
        self._enviar_decision("REQUIERE_INFORMACION")

    def action_reintentar_alta(self):
        # La aprobación es repetible: el backend continúa donde se quedó y no duplica nada
        self._enviar_decision("APROBAR")
