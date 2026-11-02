package es.florida.vet.service;

import es.florida.vet.odoo.OdooSincronizacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PedidoOdooEventListener {
    private static final Logger log = LoggerFactory.getLogger(PedidoOdooEventListener.class);
    private final OdooSincronizacionService odoo;

    public PedidoOdooEventListener(OdooSincronizacionService odoo) { this.odoo = odoo; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sincronizar(PedidoActualizadoEvent evento) {
        try {
            var resultado = odoo.enviarPedido(evento.pedidoId());
            if (Boolean.FALSE.equals(resultado.get("exito"))) {
                log.warn("Pedido {} pendiente de sincronizar: {}", evento.pedidoId(), resultado.get("detalle"));
            }
        } catch (Exception e) {
            // El pedido conserva la bandera pendiente, que se guardó antes del commit.
            // Administración puede reintentar. Nunca reabrimos el pago ya confirmado.
            log.error("No se pudo sincronizar el pedido {} después del commit", evento.pedidoId(), e);
        }
    }
}
