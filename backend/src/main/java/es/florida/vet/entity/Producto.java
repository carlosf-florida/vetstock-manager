package es.florida.vet.entity;

import es.florida.vet.enums.CategoriaProducto;
import es.florida.vet.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/**
 * Producto del catálogo.
 *
 * stockActual    = unidades físicas que hay en el almacén
 * stockReservado = unidades comprometidas en pedidos que aún no se han expedido
 * stockDisponible (calculado) = stockActual - stockReservado
 *
 * La reserva y la salida se hacen SIEMPRE mediante las actualizaciones
 * atómicas de ProductoRepository (comprueba la condición en la propia
 * consulta SQL), de forma que dos pedidos simultáneos no puedan
 * reservar más stock del disponible.
 */
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategoriaProducto categoria;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio = BigDecimal.ZERO;

    @Column(name = "stock_actual", nullable = false)
    private int stockActual;

    @Column(name = "stock_reservado", nullable = false)
    private int stockReservado;

    @Column(name = "stock_minimo", nullable = false)
    private int stockMinimo;

    @Column(nullable = false)
    private boolean activo = true;
    private Integer odooId;                     // id en Odoo (ERP)
    private LocalDateTime odooSincronizado;

    @Version
    private Long version;   // bloqueo optimista para ediciones del producto

    public Producto() {
    }

    // ----- Cálculos -----

    public int getStockDisponible() {
        return stockActual - stockReservado;
    }

    /** Stock bajo = disponible menor o igual que el mínimo (solo productos activos). */
    public boolean isStockBajo() {
        return activo && getStockDisponible() <= stockMinimo;
    }

    // ----- Operaciones de stock usadas en entradas/salidas manuales -----

    public void registrarEntrada(int cantidad) {
        if (cantidad <= 0) {
            throw new ReglaNegocioException("La cantidad debe ser mayor que 0");
        }
        stockActual += cantidad;
    }

    public void registrarSalidaManual(int cantidad) {
        if (cantidad <= 0) {
            throw new ReglaNegocioException("La cantidad debe ser mayor que 0");
        }
        if (cantidad > getStockDisponible()) {
            throw new ReglaNegocioException("No se puede retirar más stock del disponible (disponible: "
                    + getStockDisponible() + ")");
        }
        stockActual -= cantidad;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public CategoriaProducto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProducto categoria) { this.categoria = categoria; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public int getStockActual() { return stockActual; }
    public void setStockActual(int stockActual) { this.stockActual = stockActual; }
    public int getStockReservado() { return stockReservado; }
    public int getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(int stockMinimo) { this.stockMinimo = stockMinimo; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public Integer getOdooId() { return odooId; }
    public void setOdooId(Integer v) { this.odooId = v; }
    public LocalDateTime getOdooSincronizado() { return odooSincronizado; }
    public void setOdooSincronizado(LocalDateTime f) { this.odooSincronizado = f; }
}
