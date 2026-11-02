-- IVA configurado del prototipo: precios de producto sin IVA, tasa 4% para nuevos pedidos.
-- Los pedidos existentes conservan 0% (su importe original). No se recobran ni se refirman.
-- Se puede ejecutar de nuevo sin modificar los valores existentes.
SET NAMES utf8mb4;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='iva_porcentaje')=0,
  'ALTER TABLE pedidos ADD COLUMN iva_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 0.00',
  'SELECT 1 AS iva_ya_existe');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;

ALTER TABLE pedidos ALTER COLUMN iva_porcentaje SET DEFAULT 4.00;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='pedidos' AND constraint_name='ck_ped_iva')=0,
  'ALTER TABLE pedidos ADD CONSTRAINT ck_ped_iva CHECK (iva_porcentaje >= 0 AND iva_porcentaje <= 100)',
  'SELECT 1 AS control_iva_ya_existe');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;
