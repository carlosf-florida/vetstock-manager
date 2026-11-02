package es.florida.vet.controller;

import es.florida.vet.dto.FirmaRequest;
import es.florida.vet.dto.FirmaResponse;
import es.florida.vet.dto.LineasRequest;
import es.florida.vet.dto.PedidoRequest;
import es.florida.vet.dto.PedidoResponse;
import es.florida.vet.entity.Firma;
import es.florida.vet.enums.EstadoPedido;
import es.florida.vet.service.PedidoService;
import es.florida.vet.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pedidos. La seguridad por rol ya la aplica SecurityConfig; aquí y en el
 * servicio se comprueba además la pertenencia a la clínica y, para firmar,
 * que el usuario sea EXACTAMENTE el veterinario asignado.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<PedidoResponse> listar(@RequestParam(required = false) String estado,
                                       @RequestParam(required = false) Long clinicaId,
                                       Authentication auth) {
        EstadoPedido estadoPedido = parseEstado(estado);
        return pedidoService.listar(estadoPedido, clinicaId, auth.getName(), AuthUtil.esAdmin(auth));
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id, Authentication auth) {
        return pedidoService.obtener(id, auth.getName(), AuthUtil.esAdmin(auth));
    }

    /** Crear pedido: ADMIN (en nombre de un cliente), VETERINARIO o AUXILIAR. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse crear(@Valid @RequestBody PedidoRequest datos, Authentication auth) {
        return pedidoService.crear(datos, auth.getName(), AuthUtil.esAdmin(auth));
    }

    /** Reemplazar líneas de un pedido en BORRADOR (creador o ADMIN). */
    @PutMapping("/{id}/lineas")
    public PedidoResponse modificarLineas(@PathVariable Long id, @Valid @RequestBody LineasRequest datos,
                                          Authentication auth) {
        return pedidoService.modificarLineas(id, datos, auth.getName(), AuthUtil.esAdmin(auth));
    }

    /** BORRADOR -> PENDIENTE_FIRMA: reserva el stock de forma atómica. */
    @PatchMapping("/{id}/enviar-firma")
    public PedidoResponse enviarFirma(@PathVariable Long id, Authentication auth) {
        return pedidoService.enviarFirma(id, auth.getName(), AuthUtil.esAdmin(auth));
    }

    /** PENDIENTE_FIRMA -> FIRMADO: SOLO el veterinario asignado. */
    @PatchMapping("/{id}/firmar")
    public PedidoResponse firmar(@PathVariable Long id, @Valid @RequestBody FirmaRequest datos,
                                 Authentication auth) {
        return pedidoService.firmar(id, datos, auth.getName());
    }

    @GetMapping("/{id}/firma")
    public FirmaResponse verFirma(@PathVariable Long id, Authentication auth) {
        Firma firma = pedidoService.firmaDe(id, auth.getName(), AuthUtil.esAdmin(auth));
        return FirmaResponse.desde(firma);
    }

    @GetMapping(value = "/{id}/firma/imagen", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> imagenFirma(@PathVariable Long id, Authentication auth) {
        byte[] imagen = pedidoService.imagenDeFirma(id, auth.getName(), AuthUtil.esAdmin(auth));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(imagen);
    }

    @PatchMapping("/{id}/cancelar")
    public PedidoResponse cancelar(@PathVariable Long id, Authentication auth) {
        return pedidoService.cancelar(id, auth.getName(), AuthUtil.esAdmin(auth));
    }

    // ----- Estados internos (solo ADMIN; lo protege SecurityConfig) -----

    @PatchMapping("/{id}/iniciar-preparacion")
    public PedidoResponse iniciarPreparacion(@PathVariable Long id, Authentication auth) {
        return pedidoService.cambiarEstadoInterno(id, EstadoPedido.EN_PREPARACION, auth.getName());
    }

    @PatchMapping("/{id}/preparar")
    public PedidoResponse preparar(@PathVariable Long id, Authentication auth) {
        return pedidoService.cambiarEstadoInterno(id, EstadoPedido.PREPARADO, auth.getName());
    }

    @PatchMapping("/{id}/expedir")
    public PedidoResponse expedir(@PathVariable Long id, Authentication auth) {
        return pedidoService.cambiarEstadoInterno(id, EstadoPedido.EXPEDIDO, auth.getName());
    }

    private EstadoPedido parseEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        try {
            return EstadoPedido.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new es.florida.vet.exception.ReglaNegocioException("El estado no es válido");
        }
    }
}
