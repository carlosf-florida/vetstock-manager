package es.florida.vet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Veterinario: es el CLIENTE de la distribuidora y el único que puede
 * firmar (autorizar) los pedidos de su clínica.
 */
@Entity
@Table(name = "veterinarios")
public class Veterinario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @Column(name = "numero_colegiado", unique = true, length = 20)
    private String numeroColegiado;

    private String telefono;

    public Veterinario() {
    }

    public Veterinario(Usuario usuario, Clinica clinica, String numeroColegiado, String telefono) {
        this.usuario = usuario;
        this.clinica = clinica;
        this.numeroColegiado = numeroColegiado;
        this.telefono = telefono;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Clinica getClinica() { return clinica; }
    public String getNumeroColegiado() { return numeroColegiado; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
}
