package es.florida.vet.controller;

import es.florida.vet.dto.AuxiliarRequest;
import es.florida.vet.dto.AuxiliarResponse;
import es.florida.vet.dto.ClinicaRequest;
import es.florida.vet.dto.ClinicaResponse;
import es.florida.vet.dto.VeterinarioRequest;
import es.florida.vet.dto.VeterinarioResponse;
import es.florida.vet.dto.ResponsableClinicaRequest;
import es.florida.vet.service.ClinicaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Clínicas y cuentas de clientes. Solo ADMIN (lo protege SecurityConfig). */
@RestController
@RequestMapping("/api")
public class ClinicaController {

    private final ClinicaService clinicaService;

    public ClinicaController(ClinicaService clinicaService) {
        this.clinicaService = clinicaService;
    }

    @GetMapping("/clinicas")
    public List<ClinicaResponse> clinicas() {
        return clinicaService.listarClinicas();
    }

    @PostMapping("/clinicas")
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicaResponse crearClinica(@Valid @RequestBody ClinicaRequest datos) {
        return clinicaService.crearClinica(datos);
    }

    /** Salida de expedición habitual. Cuerpo: {"destinoId": 3} o {"destinoId": null} para quitarla. */
    @PutMapping("/clinicas/{id}/salida-habitual")
    public ClinicaResponse salidaHabitual(@PathVariable Long id, @RequestBody java.util.Map<String, Long> cuerpo) {
        return clinicaService.asignarSalidaHabitual(id, cuerpo.get("destinoId"));
    }

    @PutMapping("/clinicas/{id}/responsable")
    public ClinicaResponse asignarResponsable(@PathVariable Long id,
                                               @Valid @RequestBody ResponsableClinicaRequest datos) {
        return clinicaService.asignarResponsable(id, datos.veterinarioId());
    }

    @GetMapping("/veterinarios")
    public List<VeterinarioResponse> veterinarios(Authentication authentication) {
        return clinicaService.listarVeterinariosPara(authentication.getName());
    }

    @PostMapping("/veterinarios")
    @ResponseStatus(HttpStatus.CREATED)
    public VeterinarioResponse crearVeterinario(@Valid @RequestBody VeterinarioRequest datos) {
        return clinicaService.crearVeterinario(datos);
    }

    @GetMapping("/auxiliares")
    public List<AuxiliarResponse> auxiliares() {
        return clinicaService.listarAuxiliares();
    }

    @PostMapping("/auxiliares")
    @ResponseStatus(HttpStatus.CREATED)
    public AuxiliarResponse crearAuxiliar(@Valid @RequestBody AuxiliarRequest datos) {
        return clinicaService.crearAuxiliar(datos);
    }
}
