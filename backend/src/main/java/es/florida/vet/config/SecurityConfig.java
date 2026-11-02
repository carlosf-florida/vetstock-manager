package es.florida.vet.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * Seguridad de la aplicación:
 *  - Login con usuario y contraseña (sesión HTTP, cookie JSESSIONID).
 *  - Contraseñas cifradas con BCrypt.
 *  - Permisos por rol según la URL; la pertenencia a la clínica se
 *    comprueba además en los servicios (backend), nunca solo en la interfaz.
 *
 * SIMPLIFICACIÓN ACADÉMICA: se desactiva CSRF para simplificar el frontend
 * React (en producción se activaría o se usaría un token).
 *
 * Ruta pública de pago: /api/pagos/notificacion (webhook del proveedor,
 * autenticado con la firma HMAC del cuerpo
 * dentro del servicio, no por sesión).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filtroSeguridad(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(configuracionCors()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Login abierto a todos
                .requestMatchers("/api/auth/login").permitAll()
                // Webhook del proveedor de pagos: sin sesión, autenticado con firma HMAC en el servicio
                .requestMatchers(HttpMethod.POST, "/api/pagos/notificacion").permitAll()
                // Escritura de productos, stock, clínicas y cuentas: solo el almacén
                .requestMatchers(HttpMethod.POST, "/api/productos").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/productos/**").hasRole("ADMIN")
                .requestMatchers("/api/stock/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/clinicas/**", "/api/veterinarios/**", "/api/auxiliares/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/clinicas/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/clinicas/**", "/api/auxiliares/**").hasRole("ADMIN")
                // Estados internos y logística: solo el almacén
                .requestMatchers(HttpMethod.PATCH, "/api/pedidos/*/iniciar-preparacion",
                        "/api/pedidos/*/preparar", "/api/pedidos/*/expedir").hasRole("ADMIN")
                .requestMatchers("/api/bultos/**", "/api/palets/**", "/api/destinos/**").hasRole("ADMIN")
                .requestMatchers("/api/informes/**", "/api/odoo/**").hasRole("ADMIN")
                // Subir documentos: solo el almacén
                .requestMatchers(HttpMethod.POST, "/api/pedidos/*/documentos").hasRole("ADMIN")
                // Firma: solo veterinarios. El servicio comprueba que sea el asignado al pedido.
                .requestMatchers(HttpMethod.PATCH, "/api/pedidos/*/firmar").hasRole("VETERINARIO")
                // El resto de la API: cualquier usuario con sesión iniciada
                .requestMatchers("/api/**").authenticated()
                // Archivos estáticos (build de React si se copia en el backend): públicos
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) ->
                        escribirJson(response, 200, "{\"mensaje\":\"Sesión iniciada\"}"))
                .failureHandler((request, response, excepcion) ->
                        escribirJson(response, 401, "{\"estado\":401,\"mensaje\":\"Usuario o contraseña incorrectos\"}")))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                        escribirJson(response, 200, "{\"mensaje\":\"Sesión cerrada\"}")))
            .exceptionHandling(excepciones -> excepciones
                // Sin sesión -> 401
                .authenticationEntryPoint((request, response, excepcion) ->
                        escribirJson(response, 401, "{\"estado\":401,\"mensaje\":\"Debes iniciar sesión\"}"))
                // Con sesión pero sin permiso para esa ruta -> 403
                .accessDeniedHandler((request, response, excepcion) ->
                        escribirJson(response, 403, "{\"estado\":403,\"mensaje\":\"Acceso no permitido para tu rol\"}")));
        return http.build();
    }

    /**
     * CORS para el servidor de desarrollo de Vite (http://localhost:5173).
     * Con el proxy de Vite no sería necesario, pero lo dejamos configurado
     * por si el frontend se sirve en otro puerto durante el desarrollo.
     */
    @Bean
    public CorsConfigurationSource configuracionCors() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));
        configuracion.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/**", configuracion);
        return origen;
    }

    private void escribirJson(HttpServletResponse response, int estado, String json) throws IOException {
        response.setStatus(estado);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(json);
    }
}
