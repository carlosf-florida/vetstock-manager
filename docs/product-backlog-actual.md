# Product Backlog actual

Alineado con lo implementado a 2 de noviembre de 2026. El backlog inicial del
sprint 1 se conserva en [`scrum/product-backlog.md`](scrum/product-backlog.md) como histórico.

Estado: **Hecho** = implementado y probado (automático o manual, ver `pruebas.md`) ·
**Parcial** · **Pendiente**. Una pantalla sin la regla en el backend no cuenta como hecha.

| ID | Historia de usuario | Prioridad | Estado |
|---|---|---|---|
| H-01 | Como usuario, quiero iniciar y cerrar sesión para acceder solo a lo que me corresponde | Alta | Hecho |
| H-02 | Como clínica, quiero ver el catálogo con precio y stock disponible para preparar mi pedido | Alta | Hecho |
| H-03 | Como auxiliar o veterinario, quiero un carrito y revisar el pedido antes de enviarlo | Alta | Hecho |
| H-04 | Como auxiliar, quiero dejar el pedido pendiente de firma con el stock reservado | Alta | Hecho |
| H-05 | Como veterinario asignado, quiero revisar y firmar (dibujo o imagen) el pedido | Alta | Hecho |
| H-06 | Como clínica, quiero pagar el pedido firmado por una pasarela y reintentar si falla | Alta | Hecho (pasarela de demostración) |
| H-07 | Como sistema, quiero confirmar el pago solo con una notificación verificada del proveedor | Alta | Hecho (HMAC, importe, moneda, idempotencia) |
| H-08 | Como administración, quiero que nada se prepare sin firma y pago | Alta | Hecho |
| H-09 | Como administración, quiero preparar, registrar bultos y asignarlos a palets y destinos | Alta | Hecho |
| H-10 | Como administración, quiero marcar preparado y expedido, descontando el stock una vez | Alta | Hecho |
| H-11 | Como administración, quiero crear o corregir pedidos en nombre de una clínica, con autoría | Alta | Hecho |
| H-12 | Como clínica, quiero ver solo mis pedidos, su firma, su pago y sus documentos | Alta | Hecho |
| H-13 | Como administración, quiero adjuntar PDF (pedido, factura de prueba, albarán) | Media | Hecho |
| H-14 | Como administración, quiero buscar pedidos por número, clínica, fecha y estado | Media | Hecho |
| H-15 | Como administración, quiero que las reservas sin pago caduquen | Media | Hecho (72 h) |
| H-16 | Como administración, quiero gestionar productos, stock y movimientos | Media | Hecho |
| H-17 | Como administración, quiero gestionar clínicas, veterinarios, auxiliares y responsables | Media | Parcial (falta editar o dar de baja usuarios) |
| H-18 | Como administración, quiero informes con datos reales | Media | Hecho (gráficos propios en React) |
| H-19 | Como dirección, quiero analizar los datos en Power BI | Baja | Parcial (vistas SQL listas; sin informe ni conexión comprobada) |
| H-20 | Como administración, quiero sincronizar pedidos con el ERP | Baja | Existente y opcional (Odoo) |
| H-21 | Como clínica, quiero pagar con Bizum o tarjeta reales | Baja | Pendiente (requiere contrato y credenciales) |
| H-22 | Como administración, quiero marcar un palet expedido desde la interfaz | Baja | Pendiente (el endpoint existe) |
