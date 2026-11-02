package es.florida.vet.controller;

import es.florida.vet.dto.PagoIniciadoResponse;
import es.florida.vet.dto.PagoResponse;
import es.florida.vet.service.PagoService;
import es.florida.vet.util.AuthUtil;
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
 * Pagos con el proveedor de DEMOSTRACIÓN (simulado).
 *
 * - /iniciar: usuario con sesión (ADMIN o su clínica); solo pedidos firmados.
 * - /simulado/{referencia}: con sesión; simula la página del banco (NO es una
 *   integración real) y envía al webhook una notificación firmada con HMAC.
 * - /notificacion: PÚBLICO pero autenticado con la firma HMAC del cuerpo
 *   (cabecera X-Firma-Notificacion). Aquí iría el webhook real (Redsys...).
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
                                 @RequestBody SimulacionPago cuerpo,
                                 Authentication auth) {
        String resultado = (cuerpo.resultado() == null) ? "CONFIRMADO" : cuerpo.resultado();
        return pagoService.simuladoProcesar(referencia, resultado, auth.getName(), AuthUtil.esAdmin(auth));
    }

    /** Se recibe el cuerpo tal cual (String) porque la firma HMAC se calcula sobre los bytes exactos. */
    @PostMapping("/notificacion")
    public PagoResponse notificacion(@RequestBody String cuerpo,
                                     @RequestHeader(value = PagoService.CABECERA_FIRMA, required = false) String firma) {
        return pagoService.notificacion(cuerpo, firma);
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
