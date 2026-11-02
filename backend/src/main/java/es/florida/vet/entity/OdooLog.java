package es.florida.vet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Registro de cada sincronización con Odoo: qué se envió y con qué resultado. */
@Entity
@Table(name = "odoo_log")
public class OdooLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipo;        // PRODUCTO / CLIENTE / PEDIDO / CONEXION
    private String referencia;  // código de producto, clínica o nº de pedido
    private boolean exito;
    private String detalle;
    private Integer odooId;
    private LocalDateTime fecha = LocalDateTime.now();

    public OdooLog() {
    }

    public OdooLog(String tipo, String referencia, boolean exito, String detalle, Integer odooId) {
        this.tipo = tipo;
        this.referencia = referencia;
        this.exito = exito;
        this.detalle = detalle;
        this.odooId = odooId;
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public String getReferencia() { return referencia; }
    public boolean isExito() { return exito; }
    public String getDetalle() { return detalle; }
    public Integer getOdooId() { return odooId; }
    public LocalDateTime getFecha() { return fecha; }
}
