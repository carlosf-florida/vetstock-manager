package es.florida.vet.dto;

import es.florida.vet.entity.Pedido;
import es.florida.vet.service.AutorizacionPedido;
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
                             String modificadoPorNombre, LocalDateTime fechaModificacion,
                             String motivoCancelacion, boolean devolucionPendiente,
                             AutorizacionPedido autorizacion,
                             Long salidaId, String salida, boolean salidaManual,
                             String salidaAsignadaPor, LocalDateTime fechaAsignacionSalida,
                             String salidaHabitualClinica, Long bultos) {

    /** Sin el cálculo de firma y pago (listados del panel y del dashboard). */
    public static PedidoResponse desde(Pedido p) {
        return desde(p, null, null);
    }

    /** bultos: número real de bultos registrados (null si no se ha calculado). */
    public static PedidoResponse desde(Pedido p, AutorizacionPedido autorizacion, Long bultos) {
        var salida = p.getDestino();
        var habitual = p.getClinica().getDestinoHabitual();
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
                p.getFechaModificacion(), p.getMotivoCancelacion(), p.isDevolucionPendiente(), autorizacion,
                salida == null ? null : salida.getId(), salida == null ? null : salida.getEtiqueta(),
                p.isDestinoManual(),
                p.getDestinoAsignadoPor() == null ? null : p.getDestinoAsignadoPor().getNombreCompleto(),
                p.getFechaAsignacionDestino(),
                habitual == null ? null : habitual.getEtiqueta(), bultos);
    }
}
