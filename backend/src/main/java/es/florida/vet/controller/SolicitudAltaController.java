package es.florida.vet.controller;

import es.florida.vet.dto.SolicitudAltaRequest;
import es.florida.vet.dto.SolicitudAltaResponse;
import es.florida.vet.service.ActivacionCuentaService;
import es.florida.vet.service.LimitadorEnvios;
import es.florida.vet.service.SolicitudAltaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Rutas de solicitudes. Los permisos de cada grupo están en SecurityConfig. */
@RestController
public class SolicitudAltaController {

    private final SolicitudAltaService solicitudService;
    private final ActivacionCuentaService activacionService;
    private final LimitadorEnvios limitador;

    public SolicitudAltaController(SolicitudAltaService solicitudService, ActivacionCuentaService activacionService,
                                   LimitadorEnvios limitador) {
        this.solicitudService = solicitudService;
        this.activacionService = activacionService;
        this.limitador = limitador;
    }

    // ----- Públicas (sin sesión) -----

    @PostMapping("/api/publico/solicitudes-alta")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> enviar(@Valid @RequestBody SolicitudAltaRequest datos, HttpServletRequest peticion) {
        limitador.comprobar(peticion.getRemoteAddr());
        return Map.of("referencia", solicitudService.registrar(datos));
    }

    @PostMapping("/api/publico/activacion")
    public Map<String, String> activar(@RequestBody Map<String, String> datos, HttpServletRequest peticion) {
        limitador.comprobar(peticion.getRemoteAddr());
        return Map.of("usuario", activacionService.activar(datos.get("token"), datos.get("contrasena")));
    }

    // ----- Odoo: sin sesión, autenticado con la firma del cuerpo -----

    @PostMapping("/api/integracion/odoo/solicitudes/decision")
    public SolicitudAltaResponse decision(@RequestBody String cuerpo,
                                          @RequestHeader(value = SolicitudAltaService.CABECERA_FIRMA, required = false) String firma) {
        return solicitudService.decisionFirmada(cuerpo, firma);
    }

    // ----- Administración de VetStock -----

    @GetMapping("/api/solicitudes-alta")
    public List<SolicitudAltaResponse> listar() {
        return solicitudService.listar();
    }

    @PostMapping("/api/solicitudes-alta/{id}/reintentar")
    public SolicitudAltaResponse reintentar(@PathVariable Long id) {
        return solicitudService.reintentar(id);
    }

    @PostMapping("/api/solicitudes-alta/{id}/activacion")
    public ActivacionCuentaService.EnlaceActivacion generarActivacion(@PathVariable Long id) {
        return activacionService.generarParaSolicitud(id);
    }
}
