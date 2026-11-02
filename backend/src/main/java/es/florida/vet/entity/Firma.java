package es.florida.vet.entity;

import es.florida.vet.enums.TipoFirma;
import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Firma de confirmación comercial interna de un pedido.
 *
 * Guarda la copia INMUTABLE del contenido autorizado (contenidoPedido en JSON),
 * la identidad real del firmante y la fecha. Solo es válida si la hizo el veterinario
 * asignado y la copia coincide con el contenido actual (ver AutorizacionPedido). La imagen viene de un dibujo
 * (canvas) o de un archivo PNG/JPG subido.
 *
 * Esta firma es de demostración académica: NO es una firma electrónica
 * certificada ni tiene validez legal específica.
 */
@Entity
@Table(name = "firmas")
public class Firma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.LONGVARCHAR)
    private String contenidoPedido;   // JSON con pedido y líneas autorizadas

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @jakarta.persistence.Column(length = 10)
    private TipoFirma tipoFirma;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] imagen;            // PNG/JPG, hasta 5 MB (validado antes)

    private String nombreArchivo;

    private LocalDateTime fechaFirma;

    @jakarta.persistence.Column(name = "numero_colegiado", length = 20)
    private String numeroColegiado;

    public Firma() {
    }

    public Firma(Pedido pedido, Usuario usuario, String contenidoPedido,
                 TipoFirma tipoFirma, byte[] imagen, String nombreArchivo) {
        this.pedido = pedido;
        this.usuario = usuario;
        this.contenidoPedido = contenidoPedido;
        this.tipoFirma = tipoFirma;
        this.imagen = imagen;
        this.nombreArchivo = nombreArchivo;
        this.fechaFirma = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public Usuario getUsuario() { return usuario; }
    public String getContenidoPedido() { return contenidoPedido; }
    public TipoFirma getTipoFirma() { return tipoFirma; }
    public byte[] getImagen() { return imagen; }
    public String getNombreArchivo() { return nombreArchivo; }
    public LocalDateTime getFechaFirma() { return fechaFirma; }
    public String getNumeroColegiado() { return numeroColegiado; }
    public void setNumeroColegiado(String v) { this.numeroColegiado = v; }

    /** Sustituye una firma NO válida por la del veterinario asignado (una sola firma por pedido). */
    public void sustituir(Usuario usuario, String contenidoPedido, TipoFirma tipoFirma, byte[] imagen, String nombreArchivo) {
        this.usuario = usuario;
        this.contenidoPedido = contenidoPedido;
        this.tipoFirma = tipoFirma;
        this.imagen = imagen;
        this.nombreArchivo = nombreArchivo;
        this.fechaFirma = LocalDateTime.now();
    }
}
