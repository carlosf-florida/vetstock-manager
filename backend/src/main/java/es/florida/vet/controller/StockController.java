package es.florida.vet.controller;

import es.florida.vet.dto.MovimientoRequest;
import es.florida.vet.dto.MovimientoResponse;
import es.florida.vet.service.StockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Entradas y salidas manuales de stock e historial. Solo ADMIN (lo protege SecurityConfig). */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/entrada")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoResponse entrada(@Valid @RequestBody MovimientoRequest datos, Authentication auth) {
        return stockService.entrada(datos, auth.getName());
    }

    @PostMapping("/salida")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoResponse salida(@Valid @RequestBody MovimientoRequest datos, Authentication auth) {
        return stockService.salida(datos, auth.getName());
    }

    @GetMapping("/movimientos")
    public List<MovimientoResponse> movimientos(@RequestParam(required = false) Long productoId) {
        return stockService.historial(productoId);
    }
}
