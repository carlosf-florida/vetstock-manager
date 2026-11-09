package es.florida.vet.controller;

import es.florida.vet.dto.SolicitudAltaRequest;
import es.florida.vet.dto.SolicitudAltaResponse;
import es.florida.vet.service.ActivacionCuentaService;
import es.florida.vet.service.LimitadorEnvios;
import es.florida.vet.service.SolicitudAltaService;
import es.florida.vet.entity.SolicitudDocumento;
import es.florida.vet.service.ValidadorImagen;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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
    private final ValidadorImagen validadorImagen;

    public SolicitudAltaController(SolicitudAltaService solicitudService, ActivacionCuentaService activacionService,
                                   LimitadorEnvios limitador, ValidadorImagen validadorImagen) {
        this.solicitudService = solicitudService;
        this.activacionService = activacionService;
        this.limitador = limitador;
        this.validadorImagen = validadorImagen;
    }

    // ----- Públicas (sin sesión) -----

    /** multipart: "datos" (JSON del formulario) y "documento" (imagen JPG o PNG). */
    @PostMapping(value = "/api/publico/solicitudes-alta", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> enviar(@Valid @RequestPart("datos") SolicitudAltaRequest datos,
                                      @RequestPart(value = "documento", required = false) MultipartFile documento,
                                      HttpServletRequest peticion) {
        limitador.comprobar(peticion.getRemoteAddr());
        return Map.of("referencia", solicitudService.registrar(datos, validadorImagen.validar(documento)));
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

    @GetMapping("/api/solicitudes-alta/{id}/documento")
    public ResponseEntity<byte[]> verDocumento(@PathVariable Long id) {
        SolicitudDocumento documento = solicitudService.documentoVigente(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.getTipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("acreditacion-" + id + "." + documento.extension()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(documento.getContenido());
    }

    @PostMapping(value = "/api/solicitudes-alta/{id}/documento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SolicitudAltaResponse sustituirDocumento(@PathVariable Long id, @RequestPart(value = "documento", required = false) MultipartFile documento,
                                                    Authentication autenticacion) {
        return solicitudService.sustituirDocumento(id, validadorImagen.validar(documento), autenticacion.getName());
    }

    @PostMapping("/api/solicitudes-alta/{id}/activacion")
    public ActivacionCuentaService.EnlaceActivacion generarActivacion(@PathVariable Long id) {
        return activacionService.generarParaSolicitud(id);
    }
}
