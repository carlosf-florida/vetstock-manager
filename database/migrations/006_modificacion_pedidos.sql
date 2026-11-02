-- Guarda quién cambió por última vez las líneas de un pedido (por ejemplo, administración
-- ayudando a un cliente). Se puede ejecutar varias veces.
SET NAMES utf8mb4;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='modificado_por_id')=0,
  'ALTER TABLE pedidos ADD COLUMN modificado_por_id BIGINT NULL AFTER creado_por_id, ADD COLUMN fecha_modificacion DATETIME NULL AFTER modificado_por_id',
  'SELECT 1 AS columnas_ya_existen');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='pedidos' AND constraint_name='fk_ped_modificado_por')=0,
  'ALTER TABLE pedidos ADD CONSTRAINT fk_ped_modificado_por FOREIGN KEY (modificado_por_id) REFERENCES usuarios(id)',
  'SELECT 1 AS fk_ya_existe');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;
