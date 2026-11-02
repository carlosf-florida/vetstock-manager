package es.florida.vet.controller;

import es.florida.vet.dto.BultoRequest;
import es.florida.vet.dto.BultoResponse;
import es.florida.vet.dto.DestinoRequest;
import es.florida.vet.dto.DestinoResponse;
import es.florida.vet.dto.PaletRequest;
import es.florida.vet.dto.PaletResponse;
import es.florida.vet.service.LogisticaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Bultos, palets y destinos. Solo ADMIN (lo protege SecurityConfig). */
@RestController
@RequestMapping("/api")
public class LogisticaController {

    private final LogisticaService logisticaService;

    public LogisticaController(LogisticaService logisticaService) {
        this.logisticaService = logisticaService;
    }

    @GetMapping("/bultos")
    public List<BultoResponse> bultos(@RequestParam(required = false) Long pedidoId,
                                      @RequestParam(required = false) Long paletId) {
        return logisticaService.listarBultos(pedidoId, paletId);
    }

    @PostMapping("/bultos")
    @ResponseStatus(HttpStatus.CREATED)
    public BultoResponse crearBulto(@Valid @RequestBody BultoRequest datos) {
        return logisticaService.crearBulto(datos);
    }

    @PatchMapping("/bultos/{id}/asignar-palet")
    public BultoResponse asignarPalet(@PathVariable Long id, @RequestBody Map<String, Long> cuerpo) {
        Long paletId = cuerpo.get("paletId");
        if (paletId == null) {
            throw new es.florida.vet.exception.ReglaNegocioException("Falta el palet");
        }
        return logisticaService.asignarPalet(id, paletId);
    }

    @GetMapping("/palets")
    public List<PaletResponse> palets(@RequestParam(required = false) Long destinoId) {
        return logisticaService.listarPalets(destinoId);
    }

    @GetMapping("/palets/{id}")
    public PaletResponse palet(@PathVariable Long id) {
        return logisticaService.obtenerPalet(id);
    }

    @PostMapping("/palets")
    @ResponseStatus(HttpStatus.CREATED)
    public PaletResponse crearPalet(@Valid @RequestBody PaletRequest datos) {
        return logisticaService.crearPalet(datos);
    }

    @PatchMapping("/palets/{id}/expedir")
    public PaletResponse expedirPalet(@PathVariable Long id) {
        return logisticaService.expedirPalet(id);
    }

    @GetMapping("/destinos")
    public List<DestinoResponse> destinos() {
        return logisticaService.listarDestinos();
    }

    @PostMapping("/destinos")
    @ResponseStatus(HttpStatus.CREATED)
    public DestinoResponse crearDestino(@Valid @RequestBody DestinoRequest datos) {
        return logisticaService.crearDestino(datos);
    }
}
