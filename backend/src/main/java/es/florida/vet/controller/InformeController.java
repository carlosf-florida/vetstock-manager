package es.florida.vet.controller;

import es.florida.vet.dto.InformesResponse;
import es.florida.vet.dto.ProductoResponse;
import es.florida.vet.service.InformeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Informes para administración. Solo ADMIN (lo protege SecurityConfig).
 * Los datos salen de la base de datos real; sirven también para preparar
 * la conexión con Power BI más adelante.
 */
@RestController
@RequestMapping("/api/informes")
public class InformeController {

    private final InformeService informeService;

    public InformeController(InformeService informeService) {
        this.informeService = informeService;
    }

    @GetMapping("/productos-top")
    public List<InformesResponse.ProductoTop> productosTop(@RequestParam(defaultValue = "10") int limite) {
        return informeService.productosTop(limite);
    }

    @GetMapping("/pedidos-por-mes")
    public List<InformesResponse.PedidosPorMes> pedidosPorMes() {
        return informeService.pedidosPorMes();
    }

    @GetMapping("/stock-bajo")
    public List<ProductoResponse> stockBajo() {
        return informeService.stockBajo();
    }

    @GetMapping("/pendientes")
    public InformesResponse.Pendientes pendientes() {
        return informeService.pendientes();
    }

    @GetMapping("/tiempos")
    public InformesResponse.Tiempos tiempos() {
        return informeService.tiempos();
    }

    @GetMapping("/destinos")
    public List<InformesResponse.PorDestino> destinos() {
        return informeService.porDestino();
    }
}
