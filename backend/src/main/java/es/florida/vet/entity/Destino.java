package es.florida.vet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Destino o ruta de reparto (ej.: Valencia, Alicante, Regional). Configurable por el ADMIN. */
@Entity
@Table(name = "destinos")
public class Destino {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código de salida del negocio (p. ej. "14"); no es el id interno. Puede faltar. */
    private String codigo;

    private String nombre;

    public Destino() {
    }

    public Destino(String nombre) {
        this.nombre = nombre;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    /** Texto para pantallas y Odoo: «14 - Regional», o solo el nombre si no tiene código. */
    public String getEtiqueta() {
        return (codigo == null || codigo.isBlank()) ? nombre : codigo + " - " + nombre;
    }
}
