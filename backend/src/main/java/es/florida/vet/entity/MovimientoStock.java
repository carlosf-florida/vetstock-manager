package es.florida.vet.entity;

import es.florida.vet.enums.TipoMovimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Trazabilidad de las entradas y salidas de stock. */
@Entity
@Table(name = "movimientos_stock")
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;   // solo si la salida viene de una expedición

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false, length = 150)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public MovimientoStock() {
    }

    public MovimientoStock(Producto producto, Usuario usuario, Pedido pedido,
                           TipoMovimiento tipo, int cantidad, String motivo) {
        this.producto = producto;
        this.usuario = usuario;
        this.pedido = pedido;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.motivo = motivo;
    }

    public Long getId() { return id; }
    public Producto getProducto() { return producto; }
    public Usuario getUsuario() { return usuario; }
    public TipoMovimiento getTipo() { return tipo; }
    public int getCantidad() { return cantidad; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getFecha() { return fecha; }
}
