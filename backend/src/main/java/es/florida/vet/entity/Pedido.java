package es.florida.vet.entity;

import es.florida.vet.enums.EstadoPedido;
import es.florida.vet.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de una clínica, con un veterinario firmante asignado.
 *
 * Los precios se guardan copiados en cada línea (precioUnitario) para que
 * la copia autorizada no cambie si el producto cambia de precio más tarde.
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @ManyToOne
    @JoinColumn(name = "veterinario_id", nullable = false)
    private Veterinario veterinario;   // veterinario FIRMANTE asignado

    @ManyToOne
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    @ManyToOne
    @JoinColumn(name = "modificado_por_id")
    private Usuario modificadoPor;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPedido estado = EstadoPedido.BORRADOR;

    @Column(length = 255)
    private String observaciones;

    // Tasa configurada para el prototipo; se conserva al crear el pedido.
    @Column(name = "iva_porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal ivaPorcentaje = new BigDecimal("4.00");

    @Version
    private Long version;   // bloqueo optimista

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineaPedido> lineas = new ArrayList<>();

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_envio_firma")
    private LocalDateTime fechaEnvioFirma;

    @Column(name = "fecha_firma")
    private LocalDateTime fechaFirma;

    @OneToOne(mappedBy = "pedido", fetch = FetchType.LAZY)
    private Firma firma;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "fecha_listo_preparar")
    private LocalDateTime fechaListoPreparar;

    @Column(name = "fecha_inicio_preparacion")
    private LocalDateTime fechaInicioPreparacion;

    @Column(name = "fecha_preparacion")
    private LocalDateTime fechaPreparacion;

    @Column(name = "fecha_expedicion")
    private LocalDateTime fechaExpedicion;

    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    public Pedido() {
    }

    public Pedido(Clinica clinica, Veterinario veterinario, Usuario creadoPor) {
        this.clinica = clinica;
        this.veterinario = veterinario;
        this.creadoPor = creadoPor;
    }

    // ----- Líneas -----

    public void agregarLinea(Producto producto, int cantidad) {
        LineaPedido linea = new LineaPedido(this, producto, cantidad, producto.getPrecio());
        lineas.add(linea);
    }

    public void reemplazarLineas(List<LineaPedido> nuevas) {
        lineas.clear();
        lineas.addAll(nuevas);
        for (LineaPedido linea : lineas) {
            linea.setPedido(this);
        }
    }

    /** Precios de las líneas sin IVA, copiados al crear el pedido. */
    public BigDecimal getBaseImponible() {
        BigDecimal total = BigDecimal.ZERO;
        for (LineaPedido linea : lineas) {
            total = total.add(linea.getImporte());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** IVA redondeado por línea (HALF_UP), igual que round_per_line de Odoo. */
    public BigDecimal getImporteIva() {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (LineaPedido linea : lineas) {
            BigDecimal impuesto = linea.getImporte().multiply(ivaPorcentaje)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            total = total.add(impuesto);
        }
        return total;
    }

    /** Importe a cobrar: base más IVA. Nunca lo decide el navegador. */
    public BigDecimal getTotal() {
        return getBaseImponible().add(getImporteIva());
    }

    // ----- Getters y setters -----

    public Long getId() { return id; }
    public Clinica getClinica() { return clinica; }
    public Veterinario getVeterinario() { return veterinario; }
    public Usuario getCreadoPor() { return creadoPor; }
    public Usuario getModificadoPor() { return modificadoPor; }
    public LocalDateTime getFechaModificacion() { return fechaModificacion; }

    public void registrarModificacion(Usuario usuario) {
        this.modificadoPor = usuario;
        this.fechaModificacion = LocalDateTime.now();
    }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public BigDecimal getIvaPorcentaje() { return ivaPorcentaje; }
    public String getMoneda() { return "EUR"; }
    public List<LineaPedido> getLineas() { return lineas; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaEnvioFirma() { return fechaEnvioFirma; }
    public void setFechaEnvioFirma(LocalDateTime fecha) { this.fechaEnvioFirma = fecha; }
    public LocalDateTime getFechaFirma() { return fechaFirma; }
    public void setFechaFirma(LocalDateTime fecha) { this.fechaFirma = fecha; }
    public Firma getFirma() { return firma; }
    public void setFirma(Firma firma) { this.firma = firma; }
    public LocalDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDateTime fecha) { this.fechaPago = fecha; }
    public LocalDateTime getFechaListoPreparar() { return fechaListoPreparar; }
    public void setFechaListoPreparar(LocalDateTime fecha) { this.fechaListoPreparar = fecha; }
    public LocalDateTime getFechaInicioPreparacion() { return fechaInicioPreparacion; }
    public void setFechaInicioPreparacion(LocalDateTime fecha) { this.fechaInicioPreparacion = fecha; }
    public LocalDateTime getFechaPreparacion() { return fechaPreparacion; }
    public void setFechaPreparacion(LocalDateTime fecha) { this.fechaPreparacion = fecha; }
    public LocalDateTime getFechaExpedicion() { return fechaExpedicion; }
    public void setFechaExpedicion(LocalDateTime fecha) { this.fechaExpedicion = fecha; }
    public LocalDateTime getFechaCancelacion() { return fechaCancelacion; }
    public void setFechaCancelacion(LocalDateTime fecha) { this.fechaCancelacion = fecha; }

    private Integer odooId;                     // id del sale.order en Odoo (ERP)
    private LocalDateTime odooEnviado;

    @Column(name = "odoo_sincronizacion_pendiente", nullable = false)
    private boolean odooSincronizacionPendiente;
    @Column(name = "odoo_ultimo_error", length = 255)
    private String odooUltimoError;

    public Integer getOdooId() { return odooId; }
    public void setOdooId(Integer v) { this.odooId = v; }
    public LocalDateTime getOdooEnviado() { return odooEnviado; }
    public void setOdooEnviado(LocalDateTime f) { this.odooEnviado = f; }
    public boolean isOdooSincronizacionPendiente() { return odooSincronizacionPendiente; }
    public void setOdooSincronizacionPendiente(boolean v) { this.odooSincronizacionPendiente = v; }
    public String getOdooUltimoError() { return odooUltimoError; }
    public void setOdooUltimoError(String v) { this.odooUltimoError = v; }
}
