-- PESOS FICTICIOS, SOLO PARA LA DEMOSTRACIÓN. No son pesos medidos ni de un proveedor real.
-- Peso por UNIDAD de venta, en kg, embalaje incluido (estimado a ojo).
-- Se pueden cambiar en Administración → Productos. Requiere la migración 011.
-- Solo rellena productos que todavía no tienen peso: no pisa pesos ya introducidos.
SET NAMES utf8mb4;

UPDATE productos SET peso_unitario_kg = CASE codigo
    WHEN 'MED-001' THEN 0.050   -- caja de 20 comprimidos
    WHEN 'MED-002' THEN 0.060   -- vial de 20 ml
    WHEN 'MED-003' THEN 0.080   -- frasco de 50 ml
    WHEN 'MED-004' THEN 0.020   -- dosis individual
    WHEN 'CLI-001' THEN 0.600   -- caja de 100 guantes
    WHEN 'CLI-002' THEN 0.700   -- caja de 100 jeringas
    WHEN 'CLI-003' THEN 0.150   -- caja de 12 suturas
    WHEN 'CLI-004' THEN 0.400   -- caja de 50 catéteres
    WHEN 'HIG-001' THEN 0.550   -- botella de 500 ml
    WHEN 'HIG-002' THEN 1.100   -- botella de 1 L
    WHEN 'ALI-001' THEN 12.500  -- saco de 12 kg con embalaje (2 sacos = 25 kg exactos)
    WHEN 'ALI-002' THEN 4.800   -- pack de 12 latas
END
WHERE peso_unitario_kg IS NULL
  AND codigo IN ('MED-001','MED-002','MED-003','MED-004','CLI-001','CLI-002','CLI-003','CLI-004',
                 'HIG-001','HIG-002','ALI-001','ALI-002');
