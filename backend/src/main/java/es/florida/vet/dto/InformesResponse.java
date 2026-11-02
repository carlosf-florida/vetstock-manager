package es.florida.vet.dto;

import java.math.BigDecimal;
import java.util.List;

/** DTOs de los informes (datos reales del prototipo; sirven también para preparar Power BI). */
public class InformesResponse {
    private InformesResponse() {
    }

    public record ProductoTop(String codigo, String nombre, long unidades, BigDecimal importe) {
    }

    public record PedidosPorMes(String mes, long pedidos, BigDecimal importe) {
    }

    public record Pendientes(long porFirma, long porPago, List<PedidoResponse> pedidosPorFirma,
                             List<PedidoResponse> pedidosPorPago) {
    }

    public record Tiempos(Double mediaCreacionFirmaHoras, Double mediaFirmaPagoHoras,
                          Double mediaPagoExpedicionHoras, Double mediaTotalHoras) {
    }

    public record PorDestino(String destino, long pedidos, long bultos) {
    }
}
