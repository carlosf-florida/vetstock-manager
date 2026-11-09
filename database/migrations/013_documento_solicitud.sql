-- 013: documento que acredita la relación del veterinario responsable con la clínica.
--   solicitud_documentos       imagen (JPG o PNG) guardada en la base, como las firmas y los PDF.
--                              Nunca se sirve en una ruta pública. Al sustituirla se conserva la
--                              anterior con vigente = FALSE.
--   solicitudes_alta.documento_*  revisión manual del documento (quién, cuándo, resultado).
-- Las solicitudes que ya existían quedan como SIN_DOCUMENTO: no se marcan como verificadas.
-- Solo añade estructura y se puede ejecutar varias veces.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS solicitud_documentos (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    solicitud_id     BIGINT       NOT NULL,
    nombre_original  VARCHAR(150) NOT NULL,   -- solo para mostrarlo; nunca se usa como ruta
    tipo             VARCHAR(20)  NOT NULL,   -- image/jpeg o image/png, comprobado por su contenido
    tamano           INT          NOT NULL,
    ancho            INT          NOT NULL,
    alto             INT          NOT NULL,
    sha256           CHAR(64)     NOT NULL,
    contenido        MEDIUMBLOB   NOT NULL,
    vigente          BOOLEAN      NOT NULL DEFAULT TRUE,
    subido_por       VARCHAR(100),            -- NULL: lo envió la clínica desde el formulario
    fecha_subida     DATETIME     NOT NULL,
    odoo_adjunto_id  INT,                     -- adjunto (ir.attachment) en Odoo, cuando se confirma
    CONSTRAINT fk_doc_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitudes_alta(id),
    INDEX idx_doc_solicitud (solicitud_id, vigente)
) ENGINE=InnoDB;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='solicitudes_alta' AND column_name='documento_estado')=0,
  'ALTER TABLE solicitudes_alta ADD COLUMN documento_estado VARCHAR(20) NOT NULL DEFAULT ''SIN_DOCUMENTO'', ADD COLUMN documento_revisado_por VARCHAR(100) NULL, ADD COLUMN documento_fecha_revision DATETIME NULL, ADD COLUMN documento_observaciones VARCHAR(500) NULL',
  'SELECT 1');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;
