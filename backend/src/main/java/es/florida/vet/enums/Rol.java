package es.florida.vet.enums;

/**
 * Roles de la aplicación.
 *
 * ADMIN       -> personal del almacén (gestiona todo el ciclo interno)
 * VETERINARIO -> cliente con cuenta propia, puede FIRMAR sus pedidos
 * AUXILIAR    -> cliente de una clínica, prepara pedidos pero NO puede firmarlos
 */
public enum Rol {
    ADMIN,
    VETERINARIO,
    AUXILIAR
}
