-- Solicitudes de alta de clínicas y activación de cuentas. No modifica datos existentes.
--   solicitudes_alta       lo enviado por el formulario público: NO es una clínica ni una cuenta
--   activaciones_cuenta    enlaces de un solo uso (solo se guarda el hash del enlace)
--   clinicas.*             identificador fiscal y email (opcionales) y referencia de la solicitud de origen
--   veterinarios.*         email (opcional) y contacto de Odoo
-- Se puede ejecutar varias veces.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS solicitudes_alta (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    referencia          VARCHAR(20)  NOT NULL UNIQUE,
    clinica_nombre      VARCHAR(100) NOT NULL,
    razon_social        VARCHAR(120),
    identificador_fiscal VARCHAR(20),
    email               VARCHAR(120) NOT NULL,
    telefono            VARCHAR(20)  NOT NULL,
    direccion           VARCHAR(70)  NOT NULL,
    codigo_postal       VARCHAR(10)  NOT NULL,
    localidad           VARCHAR(30)  NOT NULL,
    provincia           VARCHAR(30)  NOT NULL,
    pais                VARCHAR(40)  NOT NULL,
    vet_nombre          VARCHAR(100) NOT NULL,
    vet_colegiado       VARCHAR(20)  NOT NULL,
    vet_colegio         VARCHAR(60)  NOT NULL,
    vet_email           VARCHAR(120) NOT NULL,
    vet_telefono        VARCHAR(20),
    observaciones       VARCHAR(500),
    fecha_privacidad    DATETIME     NOT NULL,   -- cuándo confirmó haber leído la información de privacidad
    huella              CHAR(64)     NOT NULL,   -- detecta el mismo envío repetido (doble clic)
    fecha_creacion      DATETIME     NOT NULL,
    estado_revision     VARCHAR(25)  NOT NULL,   -- PENDIENTE, REQUIERE_INFORMACION, APROBADA, RECHAZADA
    revisado_por        VARCHAR(100),
    fecha_revision      DATETIME,
    observaciones_revision VARCHAR(500),
    estado_alta         VARCHAR(25)  NOT NULL,   -- NO_INICIADA, EN_PROCESO, REQUIERE_REVISION, FALLIDA, COMPLETADA
    mensaje_alta        VARCHAR(255),
    clinica_id          BIGINT,
    veterinario_id      BIGINT,
    odoo_id             INT,                     -- registro de la solicitud en Odoo
    odoo_pendiente      BOOLEAN      NOT NULL DEFAULT TRUE,
    odoo_ultimo_error   VARCHAR(255),
    CONSTRAINT fk_sol_clinica FOREIGN KEY (clinica_id) REFERENCES clinicas(id),
    CONSTRAINT fk_sol_veterinario FOREIGN KEY (veterinario_id) REFERENCES veterinarios(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS activaciones_cuenta (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT   NOT NULL,
    token_hash  CHAR(64) NOT NULL UNIQUE,
    caduca      DATETIME NOT NULL,
    usada       DATETIME,
    creada      DATETIME NOT NULL,
    CONSTRAINT fk_act_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='clinicas' AND column_name='identificador_fiscal')=0,
  'ALTER TABLE clinicas ADD COLUMN identificador_fiscal VARCHAR(20) NULL, ADD COLUMN email VARCHAR(120) NULL, ADD COLUMN solicitud_referencia VARCHAR(20) NULL, ADD CONSTRAINT uq_clinica_fiscal UNIQUE (identificador_fiscal)',
  'SELECT 1');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='veterinarios' AND column_name='email')=0,
  'ALTER TABLE veterinarios ADD COLUMN email VARCHAR(120) NULL, ADD COLUMN odoo_contacto_id INT NULL',
  'SELECT 1');
PREPARE cambio FROM @sql; EXECUTE cambio; DEALLOCATE PREPARE cambio;
