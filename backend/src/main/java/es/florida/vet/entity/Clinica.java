package es.florida.vet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

/** Clínica cliente de la distribuidora. Veterinarios y auxiliares pertenecen a una. */
@Entity
@Table(name = "clinicas")
public class Clinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String direccion;
    private String telefono;
    private boolean activa = true;
    private Integer odooId;                     // id en Odoo (res.partner)
    private LocalDateTime odooSincronizado;

    @ManyToOne
    @JoinColumn(name = "veterinario_responsable_id")
    private Veterinario veterinarioResponsable;

    public Clinica() {
    }

    public Clinica(String nombre, String direccion, String telefono) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.telefono = telefono;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public Integer getOdooId() { return odooId; }
    public void setOdooId(Integer v) { this.odooId = v; }
    public LocalDateTime getOdooSincronizado() { return odooSincronizado; }
    public void setOdooSincronizado(LocalDateTime f) { this.odooSincronizado = f; }
    public Veterinario getVeterinarioResponsable() { return veterinarioResponsable; }
    public void setVeterinarioResponsable(Veterinario veterinarioResponsable) {
        this.veterinarioResponsable = veterinarioResponsable;
    }
}
