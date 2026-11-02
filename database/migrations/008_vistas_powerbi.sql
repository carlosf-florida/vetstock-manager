-- Vistas de SOLO LECTURA para Power BI (u otra herramienta de informes).
-- Usan únicamente datos reales de las tablas del proyecto. Power BI se conecta
-- a MySQL con un usuario que solo puede leer estas vistas (ver docs/power-bi.md).
-- Se puede ejecutar varias veces (CREATE OR REPLACE).
SET NAMES utf8mb4;

-- Una fila por línea de pedido: productos más pedidos, unidades, importes y evolución mensual
CREATE OR REPLACE VIEW v_pbi_lineas_pedido AS
SELECT l.id                                   AS linea_id,
       p.id                                   AS pedido_id,
       DATE(p.fecha_creacion)                 AS fecha_pedido,
       DATE_FORMAT(p.fecha_creacion, '%Y-%m') AS mes,
       p.estado                               AS estado_pedido,
       c.nombre                               AS clinica,
       pr.codigo                              AS producto_codigo,
       pr.nombre                              AS producto,
       pr.categoria                           AS categoria,
       l.cantidad                             AS unidades,
       l.precio_unitario                      AS precio_unitario,
       ROUND(l.cantidad * l.precio_unitario, 2)                                AS importe_base,
       ROUND(l.cantidad * l.precio_unitario * p.iva_porcentaje / 100, 2)       AS importe_iva,
       (p.fecha_pago IS NOT NULL)             AS pagado
  FROM lineas_pedido l
  JOIN pedidos p    ON p.id = l.pedido_id
  JOIN clinicas c   ON c.id = p.clinica_id
  JOIN productos pr ON pr.id = l.producto_id;

-- Una fila por pedido: estados de firma/pago y tiempos entre hitos (en horas)
CREATE OR REPLACE VIEW v_pbi_pedidos AS
SELECT p.id                                   AS pedido_id,
       c.nombre                               AS clinica,
       p.estado                               AS estado,
       (p.fecha_firma IS NOT NULL)            AS firmado,
       (p.fecha_pago IS NOT NULL)             AS pagado,
       p.fecha_creacion, p.fecha_envio_firma, p.fecha_firma, p.fecha_pago,
       p.fecha_inicio_preparacion, p.fecha_preparacion, p.fecha_expedicion, p.fecha_cancelacion,
       p.motivo_cancelacion,
       TIMESTAMPDIFF(MINUTE, p.fecha_creacion, p.fecha_firma) / 60.0    AS horas_creacion_a_firma,
       TIMESTAMPDIFF(MINUTE, p.fecha_firma, p.fecha_pago) / 60.0        AS horas_firma_a_pago,
       TIMESTAMPDIFF(MINUTE, p.fecha_pago, p.fecha_expedicion) / 60.0   AS horas_pago_a_expedicion,
       TIMESTAMPDIFF(MINUTE, p.fecha_creacion, p.fecha_expedicion) / 60.0 AS horas_totales,
       (SELECT ROUND(SUM(l.cantidad * l.precio_unitario), 2) FROM lineas_pedido l WHERE l.pedido_id = p.id) AS importe_base,
       (SELECT COUNT(*) FROM bultos b WHERE b.pedido_id = p.id) AS bultos
  FROM pedidos p
  JOIN clinicas c ON c.id = p.clinica_id;

-- Stock actual por producto con el aviso de stock bajo
CREATE OR REPLACE VIEW v_pbi_stock AS
SELECT pr.codigo, pr.nombre, pr.categoria, pr.activo,
       pr.stock_actual, pr.stock_reservado,
       (pr.stock_actual - pr.stock_reservado)        AS stock_disponible,
       pr.stock_minimo,
       ((pr.stock_actual - pr.stock_reservado) <= pr.stock_minimo) AS stock_bajo
  FROM productos pr;

-- Bultos con su palet y destino: pedidos y bultos por destino, contenido de cada palet
CREATE OR REPLACE VIEW v_pbi_bultos_destino AS
SELECT b.identificador                 AS bulto,
       b.fecha_alta,
       b.pedido_id,
       c.nombre                        AS clinica,
       pa.identificador                AS palet,
       COALESCE(d.nombre, 'Sin asignar') AS destino,
       pa.fecha_expedicion             AS fecha_expedicion_palet
  FROM bultos b
  JOIN pedidos p    ON p.id = b.pedido_id
  JOIN clinicas c   ON c.id = p.clinica_id
  LEFT JOIN palets pa  ON pa.id = b.palet_id
  LEFT JOIN destinos d ON d.id = pa.destino_id;
