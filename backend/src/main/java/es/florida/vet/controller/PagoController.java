package es.florida.vet.controller;

import es.florida.vet.dto.NotificacionPagoRequest;
import es.florida.vet.dto.PagoIniciadoResponse;
import es.florida.vet.dto.PagoResponse;
import es.florida.vet.service.PagoService;
import es.florida.vet.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pagos con el proveedor SIMULADO.
 *
 * - /iniciar: usuario con sesión (ADMIN o su clínica).
 * - /simulado/{referencia}: PÚBLICO, simula la página del banco (NO es una
 *   integración real). Llama internamente a la notificación.
 * - /notificacion: PÚBLICO pero autenticado con el token del proveedor
 *   (cabecera X-Notificacion-Token). Aquí iría el webhook real (Redsys...).
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping("/{pedidoId}/iniciar")
    @ResponseStatus(HttpStatus.CREATED)
    public PagoIniciadoResponse iniciar(@PathVariable Long pedidoId, Authentication auth) {
        return pagoService.iniciar(pedidoId, auth.getName(), AuthUtil.esAdmin(auth));
    }

    @PostMapping("/simulado/{referencia}")
    public PagoResponse simulado(@PathVariable String referencia,
                                 @RequestBody SimulacionPago cuerpo) {
        String resultado = (cuerpo.resultado() == null) ? "CONFIRMADO" : cuerpo.resultado();
        return pagoService.simuladoProcesar(referencia, resultado);
    }

    @PostMapping("/notificacion")
    public PagoResponse notificacion(@Valid @RequestBody NotificacionPagoRequest datos,
                                     @RequestHeader(value = "X-Notificacion-Token", required = false) String token) {
        return pagoService.notificacion(datos, token);
    }

    @GetMapping
    public List<PagoResponse> listar(@RequestParam(required = false) Long pedidoId,
                                     @RequestParam(required = false) String estado,
                                     Authentication auth) {
        return pagoService.listar(pedidoId, estado, auth.getName(), AuthUtil.esAdmin(auth));
    }

    public record SimulacionPago(String resultado) {
    }
}
