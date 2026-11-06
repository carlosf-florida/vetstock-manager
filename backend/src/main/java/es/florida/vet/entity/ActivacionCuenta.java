package es.florida.vet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Enlace de un solo uso para elegir contraseña. Solo se guarda el hash: el enlace no se puede recuperar. */
@Entity
@Table(name = "activaciones_cuenta")
public class ActivacionCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private String tokenHash;
    private LocalDateTime caduca;
    private LocalDateTime usada;
    private LocalDateTime creada = LocalDateTime.now();

    protected ActivacionCuenta() {
    }

    public ActivacionCuenta(Usuario usuario, String tokenHash, LocalDateTime caduca) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.caduca = caduca;
    }

    public boolean vigente() { return usada == null && caduca.isAfter(LocalDateTime.now()); }
    public void marcarUsada() { this.usada = LocalDateTime.now(); }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getCaduca() { return caduca; }
}
