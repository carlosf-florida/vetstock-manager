-- Peso por unidad de cada producto, para calcular los bultos del pedido (máx. 25 kg por bulto).
--   productos.peso_unitario_kg  kilos por UNIDAD de venta, con 3 decimales (gramos exactos).
--                               NULL = peso desconocido (nunca se trata como 0).
-- No rellena pesos: los pesos ficticios de la demo están aparte, en database/demo/pesos_ficticios_demo.sql.
-- Se puede ejecutar varias veces.
SET NAMES utf8mb4;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='peso_unitario_kg')=0,
  'ALTER TABLE productos ADD COLUMN peso_unitario_kg DECIMAL(10,3) NULL AFTER precio, ADD CONSTRAINT ck_prod_peso CHECK (peso_unitario_kg IS NULL OR peso_unitario_kg > 0)',
  'SELECT 1');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;
