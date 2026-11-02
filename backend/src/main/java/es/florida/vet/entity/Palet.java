package es.florida.vet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Palet de expedición: agrupa bultos y tiene un destino. */
@Entity
@Table(name = "palets")
public class Palet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String identificador;

    @ManyToOne
    @JoinColumn(name = "destino_id", nullable = false)
    private Destino destino;

    @OneToMany(mappedBy = "palet")
    private List<Bulto> bultos = new ArrayList<>();

    @Column(name = "fecha_expedicion")
    private LocalDateTime fechaExpedicion;

    public Palet() {
    }

    public Palet(String identificador, Destino destino) {
        this.identificador = identificador;
        this.destino = destino;
    }

    public Long getId() { return id; }
    public String getIdentificador() { return identificador; }
    public Destino getDestino() { return destino; }
    public List<Bulto> getBultos() { return bultos; }
    public LocalDateTime getFechaExpedicion() { return fechaExpedicion; }
    public void marcarExpedido() { this.fechaExpedicion = LocalDateTime.now(); }
}
