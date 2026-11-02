package es.florida.vet.entity;

import es.florida.vet.enums.EstadoPago;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Intento de pago de un pedido a través de una pasarela.
 *
 * proveedor = SIMULADO (adaptador de pruebas). En producción sería Redsys u otro,
 * y el endpoint de notificación sería el webhook autenticado del proveedor.
 *
 * Un pedido puede tener varios intentos (si uno FALLA se reintenta con una
 * referencia nueva). La columna `referencia` es única: las notificaciones
 * repetidas no duplican efectos (idempotencia).
 */
@Entity
@Table(name = "pagos")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    private String proveedor = "SIMULADO";

    @Column(nullable = false, unique = true, length = 64)
    private String referencia;

    @Enumerated(EnumType.STRING)
    private EstadoPago estado = EstadoPago.PENDIENTE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal importe;

    @Column(nullable = false, length = 3)
    private String moneda = "EUR";

    @Column(name = "token_notificacion", nullable = false, length = 64)
    private String tokenNotificacion;   // secreto para autenticar la notificación del proveedor

    private int intentos = 0;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio = LocalDateTime.now();

    @Column(name = "fecha_resultado")
    private LocalDateTime fechaResultado;

    public Pago() {
    }

    public Pago(Pedido pedido, BigDecimal importe, String referencia, String tokenNotificacion) {
        this.pedido = pedido;
        this.importe = importe;
        this.referencia = referencia;
        this.tokenNotificacion = tokenNotificacion;
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public String getProveedor() { return proveedor; }
    public String getReferencia() { return referencia; }
    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }
    public BigDecimal getImporte() { return importe; }
    public String getMoneda() { return moneda; }
    public String getTokenNotificacion() { return tokenNotificacion; }
    public int getIntentos() { return intentos; }
    public void incrementarIntentos() { this.intentos++; }
    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public LocalDateTime getFechaResultado() { return fechaResultado; }
    public void setFechaResultado(LocalDateTime fechaResultado) { this.fechaResultado = fechaResultado; }
}
