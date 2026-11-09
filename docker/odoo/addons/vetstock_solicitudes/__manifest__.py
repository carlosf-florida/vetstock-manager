{
    "name": "VetStock - Solicitudes de alta de clínicas",
    "version": "18.0.1.1.0",
    "summary": "Revisión de las solicitudes de alta recibidas desde la web de VetStock Manager",
    "category": "Sales",
    "license": "LGPL-3",
    "depends": ["mail", "sale_management"],
    "data": [
        "security/grupos.xml",
        "security/ir.model.access.csv",
        "views/solicitud_alta_views.xml",
    ],
    "installable": True,
    "application": False,
}
