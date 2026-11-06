package es.florida.vet.entity;

import es.florida.vet.dto.SolicitudAltaRequest;
import es.florida.vet.enums.EstadoAltaSolicitud;
import es.florida.vet.enums.EstadoRevisionSolicitud;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Lo que una clínica envía desde la web pública. Es una petición, no una clínica: no da acceso a
 * nada. La revisión la decide una persona en Odoo; el alta (clínica, veterinario, contactos) se
 * sigue aparte porque puede quedar a medias si falla un paso.
 */
@Entity
@Table(name = "solicitudes_alta")
public class SolicitudAlta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String referencia;

    private String clinicaNombre;
    private String razonSocial;
    private String identificadorFiscal;
    private String email;
    private String telefono;
    private String direccion;
    private String codigoPostal;
    private String localidad;
    private String provincia;
    private String pais;
    private String vetNombre;
    private String vetColegiado;
    private String vetColegio;
    private String vetEmail;
    private String vetTelefono;
    private String observaciones;
    private LocalDateTime fechaPrivacidad;
    private String huella;
    private LocalDateTime fechaCreacion;

    @Enumerated(EnumType.STRING)
    private EstadoRevisionSolicitud estadoRevision = EstadoRevisionSolicitud.PENDIENTE;
    private String revisadoPor;
    private LocalDateTime fechaRevision;
    private String observacionesRevision;

    @Enumerated(EnumType.STRING)
    private EstadoAltaSolicitud estadoAlta = EstadoAltaSolicitud.NO_INICIADA;
    private String mensajeAlta;
    private Long clinicaId;
    private Long veterinarioId;

    private Integer odooId;
    private boolean odooPendiente = true;
    private String odooUltimoError;

    protected SolicitudAlta() {
    }

    public SolicitudAlta(String referencia, String huella, SolicitudAltaRequest d) {
        this.referencia = referencia;
        this.huella = huella;
        this.clinicaNombre = d.clinicaNombre().trim();
        this.razonSocial = limpio(d.razonSocial());
        this.identificadorFiscal = normalizarFiscal(d.identificadorFiscal());
        this.email = d.email().trim().toLowerCase();
        this.telefono = d.telefono().trim();
        this.direccion = d.direccion().trim();
        this.codigoPostal = d.codigoPostal().trim();
        this.localidad = d.localidad().trim();
        this.provincia = d.provincia().trim();
        this.pais = d.pais().trim();
        this.vetNombre = d.vetNombre().trim();
        this.vetColegiado = d.vetColegiado().trim();
        this.vetColegio = d.vetColegio().trim();
        this.vetEmail = d.vetEmail().trim().toLowerCase();
        this.vetTelefono = limpio(d.vetTelefono());
        this.observaciones = limpio(d.observaciones());
        this.fechaPrivacidad = LocalDateTime.now();
        this.fechaCreacion = LocalDateTime.now();
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /** Sin espacios ni guiones y en mayúsculas. No comprueba el formato: depende del país. */
    public static String normalizarFiscal(String texto) {
        return texto == null || texto.isBlank() ? null : texto.replaceAll("[\\s-]", "").toUpperCase();
    }

    public void registrarRevision(EstadoRevisionSolicitud estado, String revisor, String nota) {
        this.estadoRevision = estado;
        this.revisadoPor = revisor;
        this.fechaRevision = LocalDateTime.now();
        this.observacionesRevision = nota;
    }

    public void marcarAlta(EstadoAltaSolicitud estado, String mensaje) {
        this.estadoAlta = estado;
        this.mensajeAlta = mensaje == null ? null : mensaje.substring(0, Math.min(mensaje.length(), 255));
        this.odooPendiente = true;
    }

    public void vincular(Long clinicaId, Long veterinarioId) {
        this.clinicaId = clinicaId;
        this.veterinarioId = veterinarioId;
    }

    public void registrarEnvioOdoo(Integer odooId) {
        this.odooId = odooId;
        this.odooPendiente = false;
        this.odooUltimoError = null;
    }

    public void registrarFalloOdoo(String error) {
        this.odooPendiente = true;
        this.odooUltimoError = error == null ? null : error.substring(0, Math.min(error.length(), 255));
    }

    public void marcarOdooAlDia() { this.odooPendiente = false; }

    public Long getId() { return id; }
    public String getReferencia() { return referencia; }
    public String getClinicaNombre() { return clinicaNombre; }
    public String getRazonSocial() { return razonSocial; }
    public String getIdentificadorFiscal() { return identificadorFiscal; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public String getDireccion() { return direccion; }
    public String getCodigoPostal() { return codigoPostal; }
    public String getLocalidad() { return localidad; }
    public String getProvincia() { return provincia; }
    public String getPais() { return pais; }
    public String getVetNombre() { return vetNombre; }
    public String getVetColegiado() { return vetColegiado; }
    public String getVetColegio() { return vetColegio; }
    public String getVetEmail() { return vetEmail; }
    public String getVetTelefono() { return vetTelefono; }
    public String getObservaciones() { return observaciones; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public String getHuella() { return huella; }
    public EstadoRevisionSolicitud getEstadoRevision() { return estadoRevision; }
    public String getRevisadoPor() { return revisadoPor; }
    public LocalDateTime getFechaRevision() { return fechaRevision; }
    public String getObservacionesRevision() { return observacionesRevision; }
    public EstadoAltaSolicitud getEstadoAlta() { return estadoAlta; }
    public String getMensajeAlta() { return mensajeAlta; }
    public Long getClinicaId() { return clinicaId; }
    public Long getVeterinarioId() { return veterinarioId; }
    public Integer getOdooId() { return odooId; }
    public boolean isOdooPendiente() { return odooPendiente; }
    public String getOdooUltimoError() { return odooUltimoError; }
}
