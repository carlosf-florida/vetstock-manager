package es.florida.vet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Imagen que acredita la relación del veterinario responsable con la clínica. Se guarda en la base,
 * como las firmas, y solo la ven administración de VetStock y los revisores de Odoo.
 */
@Entity
@Table(name = "solicitud_documentos")
public class SolicitudDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long solicitudId;
    private String nombreOriginal;
    private String tipo;
    private int tamano;
    private int ancho;
    private int alto;
    private String sha256;

    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] contenido;

    private boolean vigente = true;
    private String subidoPor;
    private LocalDateTime fechaSubida;
    private Integer odooAdjuntoId;

    protected SolicitudDocumento() {
    }

    public SolicitudDocumento(Long solicitudId, ImagenValidada imagen, String subidoPor) {
        this.solicitudId = solicitudId;
        this.nombreOriginal = imagen.nombre();
        this.tipo = imagen.tipo();
        this.tamano = imagen.contenido().length;
        this.ancho = imagen.ancho();
        this.alto = imagen.alto();
        this.sha256 = imagen.sha256();
        this.contenido = imagen.contenido();
        this.subidoPor = subidoPor;
        this.fechaSubida = LocalDateTime.now();
    }

    /** Resultado de validar un archivo: ya se ha comprobado que es una imagen JPG o PNG real. */
    public record ImagenValidada(String nombre, String tipo, String extension, int ancho, int alto,
                                 String sha256, byte[] contenido) {
    }

    public Long getId() { return id; }
    public Long getSolicitudId() { return solicitudId; }
    public String getNombreOriginal() { return nombreOriginal; }
    public String getTipo() { return tipo; }
    public int getTamano() { return tamano; }
    public byte[] getContenido() { return contenido; }
    public Integer getOdooAdjuntoId() { return odooAdjuntoId; }
    public String extension() { return "image/png".equals(tipo) ? "png" : "jpg"; }
}
