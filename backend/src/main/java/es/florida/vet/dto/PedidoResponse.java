package es.florida.vet.dto;

import es.florida.vet.entity.Pedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(Long id, Long clinicaId, String clinicaNombre,
                             Long veterinarioId, String veterinarioNombre,
                             String creadoPorNombre, String creadoPorUsername,
                             String estado, String observaciones, BigDecimal total,
                             LocalDateTime fechaCreacion, LocalDateTime fechaEnvioFirma,
                             LocalDateTime fechaFirma, LocalDateTime fechaPago,
                             LocalDateTime fechaListoPreparar, LocalDateTime fechaInicioPreparacion,
                             LocalDateTime fechaPreparacion, LocalDateTime fechaExpedicion,
                             LocalDateTime fechaCancelacion, List<LineaPedidoResponse> lineas,
                             String estadoFirma, String estadoPago, String firmanteNombre,
                             String firmanteRol, String veterinarioUsername,
                             String numeroColegiadoResponsable, Integer odooId,
                             boolean odooSincronizacionPendiente, String odooUltimoError,
                             BigDecimal baseImponible, BigDecimal ivaPorcentaje,
                             BigDecimal importeIva, String moneda,
                             String modificadoPorNombre, LocalDateTime fechaModificacion) {
    public static PedidoResponse desde(Pedido p) {
        var firma = p.getFirma();
        return new PedidoResponse(p.getId(), p.getClinica().getId(), p.getClinica().getNombre(),
                p.getVeterinario().getId(), p.getVeterinario().getUsuario().getNombreCompleto(),
                p.getCreadoPor().getNombreCompleto(), p.getCreadoPor().getUsername(),
                p.getEstado().name(), p.getObservaciones(), p.getTotal(),
                p.getFechaCreacion(), p.getFechaEnvioFirma(), p.getFechaFirma(), p.getFechaPago(),
                p.getFechaListoPreparar(), p.getFechaInicioPreparacion(), p.getFechaPreparacion(),
                p.getFechaExpedicion(), p.getFechaCancelacion(),
                p.getLineas().stream().map(LineaPedidoResponse::desde).toList(),
                p.getFechaFirma() == null ? "PENDIENTE" : "FIRMADO",
                p.getFechaPago() == null ? "PENDIENTE" : "PAGADO",
                firma == null ? null : firma.getUsuario().getNombreCompleto(),
                firma == null ? null : firma.getUsuario().getRol().name(),
                p.getVeterinario().getUsuario().getUsername(), p.getVeterinario().getNumeroColegiado(),
                p.getOdooId(), p.isOdooSincronizacionPendiente(), p.getOdooUltimoError(),
                p.getBaseImponible(), p.getIvaPorcentaje(), p.getImporteIva(), p.getMoneda(),
                p.getModificadoPor() == null ? null : p.getModificadoPor().getNombreCompleto(),
                p.getFechaModificacion());
    }
}
