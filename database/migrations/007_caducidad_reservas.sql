-- Política de caducidad de reservas (acordada): un pedido enviado a firma sin pago
-- confirmado en 72 h se cancela automáticamente. Se guarda el motivo de cancelación.
-- Se puede ejecutar varias veces.
SET NAMES utf8mb4;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='motivo_cancelacion')=0,
  'ALTER TABLE pedidos ADD COLUMN motivo_cancelacion VARCHAR(255) NULL AFTER fecha_cancelacion',
  'SELECT 1 AS columna_ya_existe');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;

-- Firma antes de pago: los pedidos con reserva sin fecha de envío a firma
-- (datos antiguos) toman la de creación para que la caducidad les afecte igual.
UPDATE pedidos SET fecha_envio_firma = fecha_creacion
 WHERE fecha_envio_firma IS NULL AND estado IN ('PENDIENTE_FIRMA', 'FIRMADO');
