package es.florida.vet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Auxiliar de una clínica: puede preparar pedidos de SU clínica,
 * pero nunca puede firmarlos como veterinario.
 */
@Entity
@Table(name = "auxiliares")
public class Auxiliar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    public Auxiliar() {
    }

    public Auxiliar(Usuario usuario, Clinica clinica) {
        this.usuario = usuario;
        this.clinica = clinica;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Clinica getClinica() { return clinica; }
}
