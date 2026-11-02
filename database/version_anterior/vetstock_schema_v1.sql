-- ============================================================
-- VetStock Manager - Script de base de datos (MySQL 8)
-- Proyecto Intermodular 2º DAM
--
-- ATENCIÓN: los usuarios y contraseñas de este script son SOLO
-- DE PRUEBA para la demostración. No usar en un entorno real.
--   admin        / admin123   (rol ADMIN)
--   dr.garcia    / vet123     (rol VETERINARIO)
--   dra.lopez    / vet123     (rol VETERINARIO)
--   dr.martin    / vet123     (rol VETERINARIO)
-- Las contraseñas están guardadas cifradas con BCrypt.
-- ============================================================

CREATE DATABASE IF NOT EXISTS vetstock
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE vetstock;

-- ------------------------------------------------------------
-- 1. Borrado de tablas (para poder ejecutar el script varias veces)
--    Se borra en orden inverso a las dependencias (claves ajenas)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS movimientos_stock;
DROP TABLE IF EXISTS lineas_pedido;
DROP TABLE IF EXISTS pedidos;
DROP TABLE IF EXISTS productos;
DROP TABLE IF EXISTS veterinarios;
DROP TABLE IF EXISTS usuarios;

-- ------------------------------------------------------------
-- 2. Creación de tablas
-- ------------------------------------------------------------

CREATE TABLE usuarios (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password        VARCHAR(100) NOT NULL,          -- hash BCrypt
    nombre_completo VARCHAR(100) NOT NULL,
    rol             VARCHAR(20)  NOT NULL,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_usuarios_rol CHECK (rol IN ('ADMIN', 'VETERINARIO'))
) ENGINE=InnoDB;

-- Un veterinario es un cliente que tiene asociado un usuario para entrar en la app
CREATE TABLE veterinarios (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id       BIGINT       NOT NULL UNIQUE,  -- relación 1:1 con usuarios
    clinica          VARCHAR(100) NOT NULL,
    telefono         VARCHAR(20),
    direccion        VARCHAR(150),
    numero_colegiado VARCHAR(20)  NOT NULL UNIQUE,
    CONSTRAINT fk_veterinarios_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;

CREATE TABLE productos (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo          VARCHAR(20)  NOT NULL UNIQUE,
    nombre          VARCHAR(100) NOT NULL,
    descripcion     VARCHAR(255),
    categoria       VARCHAR(30)  NOT NULL,
    stock_actual    INT          NOT NULL DEFAULT 0,  -- unidades físicas en el almacén
    stock_reservado INT          NOT NULL DEFAULT 0,  -- comprometidas en pedidos no enviados
    stock_minimo    INT          NOT NULL DEFAULT 0,  -- umbral para el aviso de "stock bajo"
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_productos_categoria CHECK (categoria IN
        ('MEDICAMENTO', 'MATERIAL_CLINICO', 'HIGIENE', 'ALIMENTACION')),
    CONSTRAINT ck_productos_stock_actual    CHECK (stock_actual >= 0),
    CONSTRAINT ck_productos_stock_reservado CHECK (stock_reservado >= 0),
    CONSTRAINT ck_productos_stock_minimo    CHECK (stock_minimo >= 0),
    CONSTRAINT ck_productos_reserva_valida  CHECK (stock_reservado <= stock_actual)
) ENGINE=InnoDB;
-- El stock disponible NO se guarda: se calcula (stock_actual - stock_reservado)

CREATE TABLE pedidos (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    veterinario_id     BIGINT       NOT NULL,   -- cliente al que va dirigido el pedido
    creado_por_id      BIGINT       NOT NULL,   -- usuario ADMIN que lo crea
    autorizado_por_id  BIGINT,                  -- usuario que lo autoriza (NULL hasta entonces)
    estado             VARCHAR(30)  NOT NULL DEFAULT 'PENDIENTE_AUTORIZACION',
    observaciones      VARCHAR(255),
    fecha_creacion     DATETIME     NOT NULL,
    fecha_autorizacion DATETIME,
    fecha_preparacion  DATETIME,
    fecha_envio        DATETIME,
    fecha_cancelacion  DATETIME,
    CONSTRAINT ck_pedidos_estado CHECK (estado IN
        ('PENDIENTE_AUTORIZACION', 'AUTORIZADO', 'PREPARADO', 'ENVIADO', 'CANCELADO')),
    CONSTRAINT fk_pedidos_veterinario FOREIGN KEY (veterinario_id)    REFERENCES veterinarios(id),
    CONSTRAINT fk_pedidos_creado_por  FOREIGN KEY (creado_por_id)     REFERENCES usuarios(id),
    CONSTRAINT fk_pedidos_autorizado  FOREIGN KEY (autorizado_por_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;

CREATE INDEX idx_pedidos_estado ON pedidos(estado);

CREATE TABLE lineas_pedido (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id   BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    cantidad    INT    NOT NULL,
    CONSTRAINT ck_lineas_cantidad CHECK (cantidad > 0),
    -- Un producto no puede repetirse dentro del mismo pedido
    CONSTRAINT uq_linea_pedido_producto UNIQUE (pedido_id, producto_id),
    CONSTRAINT fk_lineas_pedido   FOREIGN KEY (pedido_id)   REFERENCES pedidos(id),
    CONSTRAINT fk_lineas_producto FOREIGN KEY (producto_id) REFERENCES productos(id)
) ENGINE=InnoDB;

CREATE TABLE movimientos_stock (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT       NOT NULL,
    usuario_id  BIGINT       NOT NULL,   -- quién registró el movimiento
    pedido_id   BIGINT,                  -- solo si la salida viene de un envío
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
-- 3. Datos de prueba
-- ------------------------------------------------------------

-- Usuarios (contraseñas de PRUEBA cifradas con BCrypt)
INSERT INTO usuarios (id, username, password, nombre_completo, rol, activo) VALUES
(1, 'admin',     '$2a$10$74R/cXdUBNOHTo9x8sQmpe8whZDG8L93ptPWPuYqemCjYkI4jjDMi', 'Laura Sánchez (Almacén)', 'ADMIN',       TRUE),
(2, 'dr.garcia', '$2a$10$ZNhlQIoHER6wvzRGTW80Ge/kBbPylv5svPoHT251ftpJa2cFJ3jri', 'Dr. Antonio García',      'VETERINARIO', TRUE),
(3, 'dra.lopez', '$2a$10$ZNhlQIoHER6wvzRGTW80Ge/kBbPylv5svPoHT251ftpJa2cFJ3jri', 'Dra. María López',        'VETERINARIO', TRUE),
(4, 'dr.martin', '$2a$10$ZNhlQIoHER6wvzRGTW80Ge/kBbPylv5svPoHT251ftpJa2cFJ3jri', 'Dr. Pablo Martín',        'VETERINARIO', TRUE);

-- Veterinarios (clientes)
INSERT INTO veterinarios (id, usuario_id, clinica, telefono, direccion, numero_colegiado) VALUES
(1, 2, 'Clínica Veterinaria San Roque', '952111222', 'Calle Larios 10, Málaga',       'COL-2901'),
(2, 3, 'Centro Veterinario Costa Sol',  '952333444', 'Avenida de Andalucía 45, Málaga', 'COL-2902'),
(3, 4, 'Hospital Veterinario Mediterráneo', '952555666', 'Calle Alcazabilla 3, Málaga', 'COL-2903');

-- Productos
-- (stock_actual y stock_reservado ya reflejan los pedidos de prueba de más abajo)
INSERT INTO productos (id, codigo, nombre, descripcion, categoria, stock_actual, stock_reservado, stock_minimo, activo) VALUES
(1,  'MED-001', 'Amoxicilina 500 mg',           'Antibiótico, caja de 20 comprimidos',     'MEDICAMENTO',      120, 10, 20, TRUE),
(2,  'MED-002', 'Meloxicam inyectable 20 ml',   'Antiinflamatorio, vial de 20 ml',         'MEDICAMENTO',       60,  6, 15, TRUE),
(3,  'MED-003', 'Ivermectina 1% 50 ml',         'Antiparasitario, frasco de 50 ml',        'MEDICAMENTO',        8,  0, 15, TRUE),
(4,  'MED-004', 'Vacuna antirrábica',           'Dosis individual',                        'MEDICAMENTO',      100, 20, 30, TRUE),
(5,  'CLI-001', 'Guantes de nitrilo',           'Caja de 100 unidades, talla M',           'MATERIAL_CLINICO',  80,  5, 20, TRUE),
(6,  'CLI-002', 'Jeringa 5 ml',                 'Caja de 100 unidades',                    'MATERIAL_CLINICO',  45, 10, 15, TRUE),
(7,  'CLI-003', 'Sutura Vicryl 3/0',            'Caja de 12 unidades',                     'MATERIAL_CLINICO',   6,  2, 10, TRUE),
(8,  'CLI-004', 'Catéter intravenoso 22G',      'Caja de 50 unidades',                     'MATERIAL_CLINICO',  30,  0, 10, TRUE),
(9,  'HIG-001', 'Clorhexidina 4% 500 ml',       'Antiséptico para uso quirúrgico',         'HIGIENE',           40,  4, 10, TRUE),
(10, 'HIG-002', 'Gel hidroalcohólico 1 L',      'Desinfección de manos',                   'HIGIENE',           25,  0, 10, TRUE),
(11, 'ALI-001', 'Pienso hipoalergénico 12 kg',  'Saco de 12 kg para perros',               'ALIMENTACION',      15,  3, 10, TRUE),
(12, 'ALI-002', 'Dieta renal en lata',          'Pack de 12 latas',                        'ALIMENTACION',       3,  0,  8, TRUE);

-- Pedidos de prueba (uno o varios en cada estado)
INSERT INTO pedidos (id, veterinario_id, creado_por_id, autorizado_por_id, estado, observaciones,
                     fecha_creacion, fecha_autorizacion, fecha_preparacion, fecha_envio, fecha_cancelacion) VALUES
(1, 1, 1, NULL, 'PENDIENTE_AUTORIZACION', 'Pedido semanal',
    '2026-09-28 09:30:00', NULL, NULL, NULL, NULL),
(2, 2, 1, 3,    'AUTORIZADO', NULL,
    '2026-09-26 11:00:00', '2026-09-26 16:45:00', NULL, NULL, NULL),
(3, 3, 1, 4,    'PREPARADO', 'Campaña de vacunación',
    '2026-09-25 10:00:00', '2026-09-25 12:20:00', '2026-09-26 08:40:00', NULL, NULL),
(4, 1, 1, 2,    'ENVIADO', NULL,
    '2026-09-15 10:00:00', '2026-09-15 13:10:00', '2026-09-16 09:00:00', '2026-09-17 11:30:00', NULL),
(5, 2, 1, NULL, 'CANCELADO', 'Cancelado por el cliente',
    '2026-09-22 12:00:00', NULL, NULL, NULL, '2026-09-22 17:20:00'),
(6, 2, 1, NULL, 'PENDIENTE_AUTORIZACION', 'Urgente',
    '2026-09-29 08:15:00', NULL, NULL, NULL, NULL),
(7, 3, 1, 4,    'ENVIADO', NULL,
    '2026-09-10 09:00:00', '2026-09-10 10:30:00', '2026-09-11 08:30:00', '2026-09-12 12:00:00', NULL);

-- Líneas de pedido
INSERT INTO lineas_pedido (pedido_id, producto_id, cantidad) VALUES
(1, 1, 10), (1, 5, 5),      -- pedido 1 (pendiente)
(2, 2, 6),  (2, 9, 4),      -- pedido 2 (autorizado)
(3, 4, 20), (3, 6, 10),     -- pedido 3 (preparado)
(4, 1, 15), (4, 8, 5),      -- pedido 4 (enviado)
(5, 3, 5),                  -- pedido 5 (cancelado)
(6, 11, 3), (6, 7, 2),      -- pedido 6 (pendiente)
(7, 9, 10), (7, 10, 5);     -- pedido 7 (enviado)

-- Movimientos de stock
-- Entradas iniciales: cantidad = stock actual + lo que ya se ha enviado en pedidos ENVIADOS
INSERT INTO movimientos_stock (producto_id, usuario_id, pedido_id, tipo, cantidad, motivo, fecha) VALUES
(1,  1, NULL, 'ENTRADA', 135, 'Stock inicial',      '2026-09-01 09:00:00'),
(2,  1, NULL, 'ENTRADA',  60, 'Stock inicial',      '2026-09-01 09:00:00'),
(3,  1, NULL, 'ENTRADA',   8, 'Stock inicial',      '2026-09-01 09:00:00'),
(4,  1, NULL, 'ENTRADA', 100, 'Stock inicial',      '2026-09-01 09:00:00'),
(5,  1, NULL, 'ENTRADA',  80, 'Stock inicial',      '2026-09-01 09:00:00'),
(6,  1, NULL, 'ENTRADA',  45, 'Stock inicial',      '2026-09-01 09:00:00'),
(7,  1, NULL, 'ENTRADA',   6, 'Stock inicial',      '2026-09-01 09:00:00'),
(8,  1, NULL, 'ENTRADA',  35, 'Stock inicial',      '2026-09-01 09:00:00'),
(9,  1, NULL, 'ENTRADA',  50, 'Stock inicial',      '2026-09-01 09:00:00'),
(10, 1, NULL, 'ENTRADA',  30, 'Stock inicial',      '2026-09-01 09:00:00'),
(11, 1, NULL, 'ENTRADA',  15, 'Stock inicial',      '2026-09-01 09:00:00'),
(12, 1, NULL, 'ENTRADA',   3, 'Stock inicial',      '2026-09-01 09:00:00');

-- Salidas generadas por los pedidos ENVIADOS (4 y 7)
INSERT INTO movimientos_stock (producto_id, usuario_id, pedido_id, tipo, cantidad, motivo, fecha) VALUES
(1,  1, 4, 'SALIDA', 15, 'Envío del pedido 4', '2026-09-17 11:30:00'),
(8,  1, 4, 'SALIDA',  5, 'Envío del pedido 4', '2026-09-17 11:30:00'),
(9,  1, 7, 'SALIDA', 10, 'Envío del pedido 7', '2026-09-12 12:00:00'),
(10, 1, 7, 'SALIDA',  5, 'Envío del pedido 7', '2026-09-12 12:00:00');

-- ------------------------------------------------------------
-- 4. Consultas de comprobación (opcional, para probar el script)
-- ------------------------------------------------------------
-- Stock disponible y productos con stock bajo (disponible <= mínimo):
-- SELECT codigo, nombre, stock_actual, stock_reservado,
--        (stock_actual - stock_reservado) AS stock_disponible, stock_minimo
-- FROM productos
-- WHERE (stock_actual - stock_reservado) <= stock_minimo;
--
-- Pedidos con su veterinario:
-- SELECT p.id, v.clinica, p.estado, p.fecha_creacion
-- FROM pedidos p JOIN veterinarios v ON v.id = p.veterinario_id
-- ORDER BY p.fecha_creacion DESC;
