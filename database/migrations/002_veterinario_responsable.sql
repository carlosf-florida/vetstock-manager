-- Responsable de firma de cada clínica.
-- Ejecutar una vez sobre instalaciones ya creadas.
ALTER TABLE clinicas
    ADD COLUMN veterinario_responsable_id BIGINT NULL AFTER odoo_sincronizado;

ALTER TABLE clinicas
    ADD CONSTRAINT fk_clinica_veterinario_responsable
    FOREIGN KEY (veterinario_responsable_id) REFERENCES veterinarios(id);

-- En instalaciones existentes se toma como responsable inicial el primer
-- veterinario registrado de cada clínica. El administrador podrá cambiarlo.
UPDATE clinicas c
JOIN (
    SELECT clinica_id, MIN(id) AS veterinario_id
    FROM veterinarios
    GROUP BY clinica_id
) v ON v.clinica_id = c.id
SET c.veterinario_responsable_id = v.veterinario_id
WHERE c.veterinario_responsable_id IS NULL;
