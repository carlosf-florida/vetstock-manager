-- Salidas de expedición. Se REUTILIZA la tabla destinos (cada palet ya va a un destino):
--   destinos.codigo              código de salida propio del negocio (p. ej. '14'), no el id interno
--   clinicas.destino_habitual_id salida habitual de la clínica (configurable en Administración)
--   pedidos.destino_id           salida asignada al pedido (al prepararlo o a mano por administración)
--   pedidos.destino_manual       TRUE si la eligió administración (no se sobrescribe al preparar)
--   pedidos.destino_asignado_por_id / fecha_asignacion_destino: quién y cuándo
-- No asigna salidas a pedidos existentes ni códigos que no estén confirmados.
-- Se puede ejecutar varias veces.
SET NAMES utf8mb4;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='destinos' AND column_name='codigo')=0,
  'ALTER TABLE destinos ADD COLUMN codigo VARCHAR(10) NULL AFTER id, ADD CONSTRAINT uq_destino_codigo UNIQUE (codigo)',
  'SELECT 1');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='clinicas' AND column_name='destino_habitual_id')=0,
  'ALTER TABLE clinicas ADD COLUMN destino_habitual_id BIGINT NULL, ADD CONSTRAINT fk_clinica_destino_habitual FOREIGN KEY (destino_habitual_id) REFERENCES destinos(id)',
  'SELECT 1');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedidos' AND column_name='destino_id')=0,
  'ALTER TABLE pedidos ADD COLUMN destino_id BIGINT NULL, ADD COLUMN destino_manual BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN destino_asignado_por_id BIGINT NULL, ADD COLUMN fecha_asignacion_destino DATETIME NULL, ADD CONSTRAINT fk_ped_destino FOREIGN KEY (destino_id) REFERENCES destinos(id), ADD CONSTRAINT fk_ped_destino_asignado_por FOREIGN KEY (destino_asignado_por_id) REFERENCES usuarios(id)',
  'SELECT 1');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;
