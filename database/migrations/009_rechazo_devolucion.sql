-- El veterinario asignado puede rechazar un pedido que no ha firmado. Si ya estaba
-- pagado, el pedido se cancela y queda marcado con devolución pendiente: el dinero
-- se devuelve fuera de la aplicación (no hay reembolsos en este proyecto).
-- Se puede ejecutar varias veces.
SET NAMES utf8mb4;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='devolucion_pendiente')=0,
  'ALTER TABLE pedidos ADD COLUMN devolucion_pendiente BOOLEAN NOT NULL DEFAULT FALSE AFTER motivo_cancelacion',
  'SELECT 1 AS columna_ya_existe');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;
