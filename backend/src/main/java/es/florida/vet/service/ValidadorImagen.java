package es.florida.vet.service;

import es.florida.vet.entity.SolicitudDocumento.ImagenValidada;
import es.florida.vet.exception.ReglaNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;

/**
 * Comprueba en el servidor que un archivo es una imagen JPG o PNG real. No se fía del atributo
 * accept del navegador, ni de la extensión, ni del tipo declarado: mira los primeros bytes, lee las
 * dimensiones de la cabecera ANTES de decodificar (una imagen enorme podría agotar la memoria) y
 * después la decodifica entera para descartar archivos dañados.
 */
@Component
public class ValidadorImagen {

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] FIRMA_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    @Value("${vetstock.solicitudes.documento-max-mb:5}")
    private int maximoMb = 5;

    @Value("${vetstock.solicitudes.documento-max-lado-px:8000}")
    private int maximoLado = 8000;

    @Value("${vetstock.solicitudes.documento-min-lado-px:200}")
    private int minimoLado = 200;

    @Value("${vetstock.solicitudes.documento-max-megapixeles:40}")
    private int maximoMegapixeles = 40;

    public int maximoMb() {
        return maximoMb;
    }

    public ImagenValidada validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException("Adjunta el documento que acredita la responsabilidad del veterinario.");
        }
        if (archivo.getSize() > (long) maximoMb * 1024 * 1024) {
            throw new ReglaNegocioException("La imagen supera los " + maximoMb + " MB permitidos.");
        }
        String nombre = limpiarNombre(archivo.getOriginalFilename());
        String extension = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se ha podido leer el archivo.");
        }

        String tipo;
        if (empiezaPor(contenido, FIRMA_PNG)) {
            tipo = "image/png";
        } else if (empiezaPor(contenido, FIRMA_JPG)) {
            tipo = "image/jpeg";
        } else {
            throw new ReglaNegocioException("El archivo debe ser una imagen JPG o PNG.");
        }
        boolean extensionCoincide = "image/png".equals(tipo) ? extension.equals("png")
                : extension.equals("jpg") || extension.equals("jpeg");
        String declarado = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        if (!extensionCoincide || !(declarado.equals(tipo) || declarado.equals("image/jpg") && tipo.equals("image/jpeg"))) {
            throw new ReglaNegocioException("La extensión o el tipo del archivo no coinciden con su contenido.");
        }

        int[] dimensiones = leerDimensiones(contenido, tipo);
        int ancho = dimensiones[0];
        int alto = dimensiones[1];
        if (ancho > maximoLado || alto > maximoLado || (long) ancho * alto > (long) maximoMegapixeles * 1_000_000) {
            throw new ReglaNegocioException("La imagen es demasiado grande: el máximo es " + maximoLado + " píxeles por lado.");
        }
        if (ancho < minimoLado || alto < minimoLado) {
            throw new ReglaNegocioException("La imagen es demasiado pequeña para leerse: el mínimo es " + minimoLado + " píxeles por lado.");
        }
        comprobarQueSeDecodifica(contenido);
        return new ImagenValidada(nombre, tipo, extension, ancho, alto, sha256(contenido), contenido);
    }

    private int[] leerDimensiones(byte[] contenido, String tipo) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReadersByMIMEType(tipo);
            if (entrada == null || !lectores.hasNext()) {
                throw new ReglaNegocioException("La imagen no es válida.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                return new int[] {lector.getWidth(0), lector.getHeight(0)};
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ReglaNegocioException regla) {
                throw regla;
            }
            throw new ReglaNegocioException("La imagen está dañada o no se puede leer.");
        }
    }

    private void comprobarQueSeDecodifica(byte[] contenido) {
        try {
            if (ImageIO.read(new ByteArrayInputStream(contenido)) == null) {
                throw new ReglaNegocioException("La imagen está dañada o no se puede leer.");
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ReglaNegocioException regla) {
                throw regla;
            }
            throw new ReglaNegocioException("La imagen está dañada o no se puede leer.");
        }
    }

    private static boolean empiezaPor(byte[] datos, byte[] firma) {
        if (datos.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (datos[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }

    /** Solo para mostrarlo: nunca se usa como ruta. */
    private static String limpiarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "documento";
        }
        String base = nombre.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return base.length() > 150 ? base.substring(base.length() - 150) : base;
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
