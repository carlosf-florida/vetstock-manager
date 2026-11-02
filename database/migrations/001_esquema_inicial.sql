-- ============================================================
-- VetStock Manager - Migración 001: esquema inicial (MySQL 8)
-- Proyecto Intermodular 2º DAM
--
-- ATENCIÓN: los usuarios y contraseñas de este script son SOLO
-- DE PRUEBA para la demostración. No usar en un entorno real.
--   admin       / admin123  (rol ADMIN, almacén)
--   dr.garcia   / vet123    (VETERINARIO, Clínica San Roque)
--   dra.lopez   / vet123    (VETERINARIO, Clínica San Roque)
--   aux.sanroque / aux123   (AUXILIAR, Clínica San Roque)
--   dr.martin   / vet123    (VETERINARIO, Hospital Mediterráneo)
-- Las contraseñas están cifradas con BCrypt.
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS vetstock
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE vetstock;

-- ------------------------------------------------------------
-- Borrado de tablas (orden inverso a las claves ajenas)
-- ------------------------------------------------------------
-- La relación circular clinicas <-> veterinarios impide borrar en orden: se
-- desactivan las claves foráneas solo durante el borrado para poder recargar.
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS documentos;
DROP TABLE IF EXISTS odoo_log;
DROP TABLE IF EXISTS bultos;
DROP TABLE IF EXISTS palets;
DROP TABLE IF EXISTS destinos;
DROP TABLE IF EXISTS pagos;
DROP TABLE IF EXISTS firmas;
DROP TABLE IF EXISTS movimientos_stock;
DROP TABLE IF EXISTS lineas_pedido;
DROP TABLE IF EXISTS pedidos;
DROP TABLE IF EXISTS productos;
DROP TABLE IF EXISTS auxiliares;
DROP TABLE IF EXISTS veterinarios;
DROP TABLE IF EXISTS clinicas;
DROP TABLE IF EXISTS usuarios;
SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- Usuarios y clínicas
-- ------------------------------------------------------------

CREATE TABLE usuarios (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password        VARCHAR(100) NOT NULL,           -- hash BCrypt
    nombre_completo VARCHAR(100) NOT NULL,
    rol             VARCHAR(20)  NOT NULL,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_usuarios_rol CHECK (rol IN ('ADMIN', 'VETERINARIO', 'AUXILIAR'))
) ENGINE=InnoDB;

-- Clínicas: los clientes (veterinarios y auxiliares) pertenecen a una clínica
CREATE TABLE clinicas (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL UNIQUE,
    direccion VARCHAR(150),
    telefono  VARCHAR(20),
    activa    BOOLEAN NOT NULL DEFAULT TRUE,
    odoo_id   INT NULL,                             -- id del cliente (res.partner) en Odoo
    odoo_sincronizado DATETIME NULL,
    veterinario_responsable_id BIGINT NULL
) ENGINE=InnoDB;

-- Un veterinario es un cliente con cuenta propia que puede firmar pedidos
CREATE TABLE veterinarios (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id       BIGINT NOT NULL UNIQUE,   -- relación 1:1 con usuarios
    clinica_id       BIGINT NOT NULL,
    numero_colegiado VARCHAR(20) NOT NULL UNIQUE,
    telefono         VARCHAR(20),
    CONSTRAINT fk_vet_usuario  FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_vet_clinica  FOREIGN KEY (clinica_id) REFERENCES clinicas(id)
) ENGINE=InnoDB;

ALTER TABLE clinicas
    ADD CONSTRAINT fk_clinica_veterinario_responsable
    FOREIGN KEY (veterinario_responsable_id) REFERENCES veterinarios(id);

-- Un auxiliar trabaja en una clínica: prepara pedidos pero NO puede firmarlos
CREATE TABLE auxiliares (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE,   -- relación 1:1 con usuarios
    clinica_id BIGINT NOT NULL,
    CONSTRAINT fk_aux_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_aux_clinica FOREIGN KEY (clinica_id) REFERENCES clinicas(id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Productos
-- ------------------------------------------------------------

CREATE TABLE productos (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo          VARCHAR(20)  NOT NULL UNIQUE,   -- referencia única
    nombre          VARCHAR(100) NOT NULL,
    descripcion     VARCHAR(255),
    categoria       VARCHAR(30)  NOT NULL,
    precio          DECIMAL(10,2) NOT NULL,         -- precio unitario con IVA aparte (fuera del MVP)
    stock_actual    INT NOT NULL DEFAULT 0,         -- unidades físicas en el almacén
    stock_reservado INT NOT NULL DEFAULT 0,         -- comprometidas en pedidos no expedidos
    stock_minimo    INT NOT NULL DEFAULT 0,         -- umbral de aviso "stock bajo"
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT NOT NULL DEFAULT 0,      -- bloqueo optimista (concurrencia)
    odoo_id         INT NULL,                       -- id del producto en Odoo (ERP)
    odoo_sincronizado DATETIME NULL,                -- última sincronización con Odoo
    CONSTRAINT ck_prod_categoria CHECK (categoria IN
        ('MEDICAMENTO', 'MATERIAL_CLINICO', 'HIGIENE', 'ALIMENTACION')),
    CONSTRAINT ck_prod_precio    CHECK (precio >= 0),
    CONSTRAINT ck_prod_actual    CHECK (stock_actual >= 0),
    CONSTRAINT ck_prod_reservado CHECK (stock_reservado >= 0),
    CONSTRAINT ck_prod_minimo    CHECK (stock_minimo >= 0)
) ENGINE=InnoDB;
-- El stock disponible NO se guarda: se calcula (stock_actual - stock_reservado)

-- ------------------------------------------------------------
-- Pedidos
-- Estados: BORRADOR -> PENDIENTE_FIRMA -> FIRMADO -> LISTO_PREPARAR
--          -> EN_PREPARACION -> PREPARADO -> EXPEDIDO
--          (y CANCELADO como final)
-- ------------------------------------------------------------

CREATE TABLE pedidos (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinica_id            BIGINT NOT NULL,   -- clínica a la que va el pedido
    veterinario_id        BIGINT NOT NULL,   -- veterinario firmante asignado
    creado_por_id         BIGINT NOT NULL,   -- usuario que lo creó (quedá registrado)
    modificado_por_id     BIGINT,            -- último usuario que cambió las líneas
    fecha_modificacion    DATETIME,
    estado                VARCHAR(30) NOT NULL DEFAULT 'BORRADOR',
    observaciones         VARCHAR(255),
    iva_porcentaje        DECIMAL(5,2) NOT NULL DEFAULT 4.00,
    version               BIGINT NOT NULL DEFAULT 0,  -- bloqueo optimista
    fecha_creacion        DATETIME NOT NULL,
    fecha_envio_firma     DATETIME,          -- cliente lo envió a firma (reserva stock)
    fecha_firma           DATETIME,          -- el veterinario lo firmó
    fecha_pago            DATETIME,          -- pago confirmado por el proveedor
    fecha_listo_preparar  DATETIME,
    fecha_inicio_preparacion DATETIME,
    fecha_preparacion     DATETIME,
    fecha_expedicion      DATETIME,
    fecha_cancelacion     DATETIME,
    motivo_cancelacion    VARCHAR(255),      -- usuario que cancela o reserva caducada
    odoo_id               INT NULL,   -- id del sale.order en Odoo (ERP)
    odoo_enviado          DATETIME,   -- cuándo se exportó a Odoo
    odoo_sincronizacion_pendiente BOOLEAN NOT NULL DEFAULT FALSE,
    odoo_ultimo_error     VARCHAR(255),
    CONSTRAINT ck_ped_estado CHECK (estado IN
        ('BORRADOR', 'PENDIENTE_FIRMA', 'FIRMADO', 'LISTO_PREPARAR',
         'EN_PREPARACION', 'PREPARADO', 'EXPEDIDO', 'CANCELADO')),
    CONSTRAINT ck_ped_iva CHECK (iva_porcentaje >= 0 AND iva_porcentaje <= 100),
    CONSTRAINT fk_ped_clinica      FOREIGN KEY (clinica_id)     REFERENCES clinicas(id),
    CONSTRAINT fk_ped_veterinario  FOREIGN KEY (veterinario_id) REFERENCES veterinarios(id),
    CONSTRAINT fk_ped_creado_por   FOREIGN KEY (creado_por_id)  REFERENCES usuarios(id),
    CONSTRAINT fk_ped_modificado_por FOREIGN KEY (modificado_por_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;

CREATE INDEX idx_ped_estado  ON pedidos(estado);
CREATE INDEX idx_ped_clinica ON pedidos(clinica_id);

CREATE TABLE lineas_pedido (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id       BIGINT NOT NULL,
    producto_id     BIGINT NOT NULL,
    cantidad        INT    NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,   -- copia del precio al crear la línea
    CONSTRAINT ck_lin_cantidad CHECK (cantidad > 0),
    -- Un producto no puede repetirse dentro del mismo pedido
    CONSTRAINT uq_lin_pedido_producto UNIQUE (pedido_id, producto_id),
    CONSTRAINT fk_lin_pedido   FOREIGN KEY (pedido_id)   REFERENCES pedidos(id),
    CONSTRAINT fk_lin_producto FOREIGN KEY (producto_id) REFERENCES productos(id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Firma del veterinario
-- ------------------------------------------------------------

CREATE TABLE firmas (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id        BIGINT NOT NULL UNIQUE,   -- una firma por pedido
    usuario_id       BIGINT NOT NULL,          -- cuenta del veterinario que firmó
    contenido_pedido TEXT NOT NULL,            -- copia inmutable de lo autorizado (JSON)
    tipo_firma       VARCHAR(10) NOT NULL,     -- DIBUJO | IMAGEN
    imagen           MEDIUMBLOB,               -- PNG/JPG, hasta 5 MB (validado en backend)
    nombre_archivo   VARCHAR(150),             -- nombre original si se subió una imagen
    fecha_firma      DATETIME NOT NULL,
    numero_colegiado  VARCHAR(20) NULL,         -- dato opcional del responsable; no autentica
    CONSTRAINT ck_fir_tipo CHECK (tipo_firma IN ('DIBUJO', 'IMAGEN')),
    CONSTRAINT fk_fir_pedido  FOREIGN KEY (pedido_id)  REFERENCES pedidos(id),
    CONSTRAINT fk_fir_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Pagos (proveedor simulado para el MVP; etiquetado SIMULADO)
-- ------------------------------------------------------------

CREATE TABLE pagos (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id             BIGINT NOT NULL,
    proveedor             VARCHAR(30)  NOT NULL DEFAULT 'SIMULADO',
    referencia            VARCHAR(64)  NOT NULL UNIQUE,  -- id del cobro; evita duplicados
    estado                VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    importe               DECIMAL(10,2) NOT NULL,         -- se valida contra el pedido
    moneda                VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    token_notificacion    VARCHAR(64)  NOT NULL,          -- secreto para autenticar la notificación
    intentos              INT          NOT NULL DEFAULT 0,
    fecha_inicio          DATETIME     NOT NULL,
    fecha_resultado       DATETIME,
    CONSTRAINT ck_pag_estado CHECK (estado IN ('PENDIENTE', 'CONFIRMADO', 'FALLADO')),
    CONSTRAINT fk_pag_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Logística: destinos, palets y bultos
-- ------------------------------------------------------------

CREATE TABLE destinos (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL UNIQUE   -- ej. Valencia, Alicante, Regional...
) ENGINE=InnoDB;

CREATE TABLE palets (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    identificador VARCHAR(30) NOT NULL UNIQUE,
    destino_id    BIGINT NOT NULL,
    fecha_expedicion DATETIME,               -- NULL hasta que se expide el palet
    CONSTRAINT fk_pal_destino FOREIGN KEY (destino_id) REFERENCES destinos(id)
) ENGINE=InnoDB;

CREATE TABLE bultos (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    identificador VARCHAR(30) NOT NULL UNIQUE,
    pedido_id     BIGINT NOT NULL,
    palet_id      BIGINT,                   -- NULL hasta que se asigna a un palet
    fecha_alta    DATETIME NOT NULL,
    CONSTRAINT fk_bul_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id),
    CONSTRAINT fk_bul_palet  FOREIGN KEY (palet_id)  REFERENCES palets(id)
) ENGINE=InnoDB;

CREATE INDEX idx_bul_pedido ON bultos(pedido_id);
CREATE INDEX idx_bul_palet  ON bultos(palet_id);

-- ------------------------------------------------------------
-- Documentos adjuntos (PDF hasta 10 MB, validado en backend)
-- ------------------------------------------------------------

CREATE TABLE documentos (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id      BIGINT NOT NULL,
    tipo           VARCHAR(10) NOT NULL,      -- PEDIDO | FACTURA | ALBARAN
    nombre_archivo VARCHAR(150) NOT NULL,
    contenido      LONGBLOB NOT NULL,
    subido_por_id  BIGINT NOT NULL,
    fecha          DATETIME NOT NULL,
    CONSTRAINT ck_doc_tipo CHECK (tipo IN ('PEDIDO', 'FACTURA', 'ALBARAN')),
    CONSTRAINT fk_doc_pedido  FOREIGN KEY (pedido_id)     REFERENCES pedidos(id),
    CONSTRAINT fk_doc_usuario FOREIGN KEY (subido_por_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Movimientos de stock (trazabilidad de entradas y salidas)
-- ------------------------------------------------------------

CREATE TABLE movimientos_stock (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT       NOT NULL,
    usuario_id  BIGINT       NOT NULL,
    pedido_id   BIGINT,                  -- solo si la salida viene de una expedición
    tipo        VARCHAR(10)  NOT NULL,
    cantidad    INT          NOT NULL,
    motivo      VARCHAR(150) NOT NULL,
    fecha       DATETIME     NOT NULL,
    CONSTRAINT ck_mov_tipo     CHECK (tipo IN ('ENTRADA', 'SALIDA')),
    CONSTRAINT ck_mov_cantidad CHECK (cantidad > 0),
    CONSTRAINT fk_mov_producto FOREIGN KEY (producto_id) REFERENCES productos(id),
    CONSTRAINT fk_mov_usuario  FOREIGN KEY (usuario_id)  REFERENCES usuarios(id),
    CONSTRAINT fk_mov_pedido   FOREIGN KEY (pedido_id)   REFERENCES pedidos(id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Registro de sincronizaciones con Odoo (ERP): qué se envió y con qué resultado
-- ------------------------------------------------------------
CREATE TABLE odoo_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo       VARCHAR(30) NOT NULL,               -- PRODUCTO / CLIENTE / PEDIDO / CONEXION
    referencia VARCHAR(100),                       -- código de producto, clínica o nº de pedido
    exito      BOOLEAN     NOT NULL,
    detalle    VARCHAR(255),
    odoo_id    INT NULL,
    fecha      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Datos de prueba
-- ------------------------------------------------------------

INSERT INTO usuarios (id, username, password, nombre_completo, rol, activo) VALUES
(1, 'admin',       '$2a$10$74R/cXdUBNOHTo9x8sQmpe8whZDG8L93ptPWPuYqemCjYkI4jjDMi', 'Laura Sánchez (Almacén)',  'ADMIN',       TRUE),
(2, 'dr.garcia',   '$2a$10$ZNhlQIoHER6wvzRGTW80Ge/kBbPylv5svPoHT251ftpJa2cFJ3jri', 'Dr. Antonio García',       'VETERINARIO', TRUE),
(3, 'dra.lopez',   '$2a$10$ZNhlQIoHER6wvzRGTW80Ge/kBbPylv5svPoHT251ftpJa2cFJ3jri', 'Dra. María López',         'VETERINARIO', TRUE),
(4, 'aux.sanroque','$2a$10$2x9Ivu8U9YGXvn8OWxSYP.UhKTOz6Q1MoTkx1b3OOXcIBCiWxKJTu', 'Sonia Ruiz (Auxiliar)',    'AUXILIAR',    TRUE),
(5, 'dr.martin',   '$2a$10$ZNhlQIoHER6wvzRGTW80Ge/kBbPylv5svPoHT251ftpJa2cFJ3jri', 'Dr. Pablo Martín',         'VETERINARIO', TRUE),
(6, 'aux.mediterraneo','$2a$10$2x9Ivu8U9YGXvn8OWxSYP.UhKTOz6Q1MoTkx1b3OOXcIBCiWxKJTu', 'Iván Torres (Auxiliar)', 'AUXILIAR', TRUE);

INSERT INTO clinicas (id, nombre, direccion, telefono, activa) VALUES
(1, 'Clínica Veterinaria San Roque',   'Calle Larios 10, Málaga',          '952111222', TRUE),
(2, 'Centro Veterinario Costa Sol',    'Avenida de Andalucía 45, Málaga',  '952333444', TRUE),
(3, 'Hospital Veterinario Mediterráneo','Calle Alcazabilla 3, Málaga',     '952555666', TRUE);

INSERT INTO veterinarios (id, usuario_id, clinica_id, numero_colegiado, telefono) VALUES
(1, 2, 1, 'COL-2901', '651111111'),
(2, 3, 1, 'COL-2902', '652222222'),
(3, 5, 3, 'COL-2903', '653333333');

UPDATE clinicas SET veterinario_responsable_id = 1 WHERE id = 1;
UPDATE clinicas SET veterinario_responsable_id = 3 WHERE id = 3;

INSERT INTO auxiliares (id, usuario_id, clinica_id) VALUES
(1, 4, 1),
(2, 6, 3);

INSERT INTO productos (id, codigo, nombre, descripcion, categoria, precio, stock_actual, stock_reservado, stock_minimo, activo) VALUES
(1,  'MED-001', 'Amoxicilina 500 mg',          'Antibiótico, caja de 20 comprimidos',    'MEDICAMENTO',      14.50, 120, 0, 20, TRUE),
(2,  'MED-002', 'Meloxicam inyectable 20 ml',  'Antiinflamatorio, vial de 20 ml',        'MEDICAMENTO',      22.90,  60, 0, 15, TRUE),
(3,  'MED-003', 'Ivermectina 1% 50 ml',        'Antiparasitario, frasco de 50 ml',       'MEDICAMENTO',      18.75,   8, 0, 15, TRUE),
(4,  'MED-004', 'Vacuna antirrábica',          'Dosis individual',                       'MEDICAMENTO',      31.00, 100, 0, 30, TRUE),
(5,  'CLI-001', 'Guantes de nitrilo',          'Caja de 100 unidades, talla M',          'MATERIAL_CLINICO',  9.95,  80, 0, 20, TRUE),
(6,  'CLI-002', 'Jeringa 5 ml',                'Caja de 100 unidades',                   'MATERIAL_CLINICO', 12.30,  45, 0, 15, TRUE),
(7,  'CLI-003', 'Sutura Vicryl 3/0',           'Caja de 12 unidades',                    'MATERIAL_CLINICO', 27.40,   6, 0, 10, TRUE),
(8,  'CLI-004', 'Catéter intravenoso 22G',     'Caja de 50 unidades',                    'MATERIAL_CLINICO', 19.60,  30, 0, 10, TRUE),
(9,  'HIG-001', 'Clorhexidina 4% 500 ml',      'Antiséptico para uso quirúrgico',        'HIGIENE',           7.25,  40, 0, 10, TRUE),
(10, 'HIG-002', 'Gel hidroalcohólico 1 L',     'Desinfección de manos',                  'HIGIENE',           5.80,  25, 0, 10, TRUE),
(11, 'ALI-001', 'Pienso hipoalergénico 12 kg', 'Saco de 12 kg para perros',              'ALIMENTACION',     42.90,  15, 0, 10, TRUE),
(12, 'ALI-002', 'Dieta renal en lata',         'Pack de 12 latas',                       'ALIMENTACION',     24.15,   3, 0,  8, TRUE);

INSERT INTO destinos (id, nombre) VALUES
(1, 'Valencia'), (2, 'Alicante'), (3, 'Regional');

-- Pedidos de ejemplo (datos ficticios). Los enviados a firma toman la fecha de carga
-- de la base de datos: si pasan 72 h sin pago, la caducidad los cancela (ver docs).
INSERT INTO pedidos (id, clinica_id, veterinario_id, creado_por_id, estado, observaciones, fecha_creacion, fecha_envio_firma, iva_porcentaje) VALUES
(1, 1, 1, 2, 'BORRADOR', 'Pedido semanal', NOW(), NULL, 4.00),
(2, 1, 2, 4, 'PENDIENTE_FIRMA', 'Urgente, preparado por la auxiliar', NOW(), NOW(), 4.00),
(3, 3, 3, 5, 'PENDIENTE_FIRMA', 'Pendiente de que el Dr. Martín revise y firme', NOW(), NOW(), 4.00);

INSERT INTO lineas_pedido (pedido_id, producto_id, cantidad, precio_unitario) VALUES
(1, 1, 10, 14.50), (1, 5, 5, 9.95),
(2, 2,  6, 22.90), (2, 9, 4,  7.25),
(3, 4, 20, 31.00), (3, 6, 10, 12.30);

-- Reservas coherentes con los pedidos PENDIENTE_FIRMA (pedidos 2 y 3)
UPDATE productos SET stock_reservado = 6 WHERE id = 2;
UPDATE productos SET stock_reservado = 4 WHERE id = 9;
UPDATE productos SET stock_reservado = 20 WHERE id = 4;
UPDATE productos SET stock_reservado = 10 WHERE id = 6;

-- Movimientos iniciales de stock
INSERT INTO movimientos_stock (producto_id, usuario_id, pedido_id, tipo, cantidad, motivo, fecha) VALUES
(1, 1, NULL, 'ENTRADA', 120, 'Stock inicial', '2026-10-01 09:00:00'),
(2, 1, NULL, 'ENTRADA',  60, 'Stock inicial', '2026-10-01 09:00:00'),
(3, 1, NULL, 'ENTRADA',   8, 'Stock inicial', '2026-10-01 09:00:00'),
(4, 1, NULL, 'ENTRADA', 100, 'Stock inicial', '2026-10-01 09:00:00'),
(5, 1, NULL, 'ENTRADA',  80, 'Stock inicial', '2026-10-01 09:00:00'),
(6, 1, NULL, 'ENTRADA',  45, 'Stock inicial', '2026-10-01 09:00:00'),
(7, 1, NULL, 'ENTRADA',   6, 'Stock inicial', '2026-10-01 09:00:00'),
(8, 1, NULL, 'ENTRADA',  30, 'Stock inicial', '2026-10-01 09:00:00'),
(9, 1, NULL, 'ENTRADA',  40, 'Stock inicial', '2026-10-01 09:00:00'),
(10,1, NULL, 'ENTRADA',  25, 'Stock inicial', '2026-10-01 09:00:00'),
(11,1, NULL, 'ENTRADA',  15, 'Stock inicial', '2026-10-01 09:00:00'),
(12,1, NULL, 'ENTRADA',   3, 'Stock inicial', '2026-10-01 09:00:00');
