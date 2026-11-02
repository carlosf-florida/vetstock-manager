package es.florida.vet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Tarea programada de la política de reservas acordada:
 *  1) Los pagos PENDIENTES abandonados (sesión de pago caducada) pasan a FALLADO.
 *  2) Los pedidos enviados a firma sin pago confirmado tras N horas (72 por defecto)
 *     se cancelan y liberan su reserva de stock.
 * Los pedidos pagados nunca se cancelan automáticamente.
 */
@Component
public class ReservaCaducidadTask {

    private static final Logger log = LoggerFactory.getLogger(ReservaCaducidadTask.class);

    private final PedidoService pedidoService;
    private final PagoService pagoService;

    @Value("${vetstock.reservas.horas-caducidad:72}")
    private long horasCaducidad;

    @Value("${vetstock.pagos.minutos-sesion:60}")
    private long minutosSesionPago;

    public ReservaCaducidadTask(PedidoService pedidoService, PagoService pagoService) {
        this.pedidoService = pedidoService;
        this.pagoService = pagoService;
    }

    @Scheduled(fixedDelayString = "${vetstock.reservas.revision-ms:900000}", initialDelay = 60000)
    public void revisar() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime inicioSesionesVivas = ahora.minusMinutes(minutosSesionPago);
        int pagos = pagoService.caducarPagosAbandonados(inicioSesionesVivas);
        int pedidos = pedidoService.caducarReservasVencidas(ahora.minusHours(horasCaducidad),
                pedido -> pagoService.tienePagoEnCurso(pedido.getId(), inicioSesionesVivas));
        if (pagos > 0 || pedidos > 0) {
            log.info("Caducidad: {} pagos abandonados marcados FALLADO, {} pedidos cancelados", pagos, pedidos);
        }
    }
}
