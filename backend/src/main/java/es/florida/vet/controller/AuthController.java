package es.florida.vet.controller;

import es.florida.vet.dto.UsuarioResponse;
import es.florida.vet.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login, logout y perfil. El login lo procesa Spring Security (SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public UsuarioResponse me(Authentication authentication) {
        return authService.perfil(authentication.getName());
    }

    @PostMapping("/logout")
    public void logout() {
        // Lo procesa Spring Security; este método solo existe para documentar la ruta
    }
}
