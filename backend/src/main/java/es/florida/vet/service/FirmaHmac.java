package es.florida.vet.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** Base64(HMAC-SHA256(secreto, cuerpo)): demuestra quién envía el cuerpo y que no se ha cambiado. */
public final class FirmaHmac {

    private FirmaHmac() {
    }

    public static String calcular(String secreto, String cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC", e);
        }
    }
}
