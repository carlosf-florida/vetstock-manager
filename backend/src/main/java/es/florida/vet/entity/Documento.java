package es.florida.vet.entity;

import es.florida.vet.enums.TipoDocumento;
import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Documento PDF adjunto a un pedido (pedido, factura o albarán).
 * Son documentos de DEMOSTRACIÓN: una "factura" aquí no es una
 * factura fiscal válida. El contenido se guarda en la base de datos
 * y solo se sirve a usuarios autorizados (nunca enlaces públicos).
 */
@Entity
@Table(name = "documentos")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    private TipoDocumento tipo;

    @Column(name = "nombre_archivo", nullable = false, length = 150)
    private String nombreArchivo;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] contenido;   // PDF, hasta 10 MB (validado antes)

    @ManyToOne
    @JoinColumn(name = "subido_por_id", nullable = false)
    private Usuario subidoPor;

    private LocalDateTime fecha = LocalDateTime.now();

    public Documento() {
    }

    public Documento(Pedido pedido, TipoDocumento tipo, String nombreArchivo, byte[] contenido, Usuario subidoPor) {
        this.pedido = pedido;
        this.tipo = tipo;
        this.nombreArchivo = nombreArchivo;
        this.contenido = contenido;
        this.subidoPor = subidoPor;
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public TipoDocumento getTipo() { return tipo; }
    public String getNombreArchivo() { return nombreArchivo; }
    public byte[] getContenido() { return contenido; }
    public LocalDateTime getFecha() { return fecha; }
    public Usuario getSubidoPor() { return subidoPor; }
}
