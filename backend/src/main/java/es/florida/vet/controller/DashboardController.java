package es.florida.vet.controller;

import es.florida.vet.service.DashboardService;
import es.florida.vet.util.AuthUtil;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Resumen inicial según el rol del usuario. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public Map<String, Object> dashboard(Authentication auth) {
        return dashboardService.resumen(auth.getName(), AuthUtil.esAdmin(auth));
    }
}
