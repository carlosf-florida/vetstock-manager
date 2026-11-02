-- Migracion 004: confirmacion interna y pago independientes.
-- No borra tablas, pedidos, firmas ni pagos existentes. Se puede repetir.
SET NAMES utf8mb4;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='firmas' AND column_name='numero_colegiado')=0,
  'ALTER TABLE firmas ADD COLUMN numero_colegiado VARCHAR(20) NULL',
  'SELECT 1 AS colegiado_ya_existe');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='odoo_sincronizacion_pendiente')=0,
  'ALTER TABLE pedidos ADD COLUMN odoo_sincronizacion_pendiente BOOLEAN NOT NULL DEFAULT FALSE',
  'SELECT 1 AS seguimiento_ya_existe');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='odoo_ultimo_error')=0,
  'ALTER TABLE pedidos ADD COLUMN odoo_ultimo_error VARCHAR(255) NULL',
  'SELECT 1 AS error_odoo_ya_existe');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;
