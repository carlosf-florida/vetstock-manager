package es.florida.vet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Bulto (caja, paquete) asociado a un pedido; puede ir dentro de un palet. */
@Entity
@Table(name = "bultos")
public class Bulto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String identificador;

    @ManyToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "palet_id")
    private Palet palet;   // NULL hasta que se asigna

    @Column(name = "fecha_alta")
    private LocalDateTime fechaAlta = LocalDateTime.now();

    public Bulto() {
    }

    public Bulto(String identificador, Pedido pedido) {
        this.identificador = identificador;
        this.pedido = pedido;
    }

    public Long getId() { return id; }
    public String getIdentificador() { return identificador; }
    public Pedido getPedido() { return pedido; }
    public Palet getPalet() { return palet; }
    public void asignarPalet(Palet palet) { this.palet = palet; }
    public void quitarPalet() { this.palet = null; }
    public LocalDateTime getFechaAlta() { return fechaAlta; }
}
