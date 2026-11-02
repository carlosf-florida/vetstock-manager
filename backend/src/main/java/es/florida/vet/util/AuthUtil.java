package es.florida.vet.util;

import org.springframework.security.core.Authentication;

/** Utilidades pequeñas sobre el usuario autenticado. */
public final class AuthUtil {

    private AuthUtil() {
    }

    /** Spring Security guarda los roles con el prefijo ROLE_. */
    public static boolean esAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(permiso -> permiso.getAuthority().equals("ROLE_ADMIN"));
    }

    public static String username(Authentication authentication) {
        return authentication.getName();
    }
}
