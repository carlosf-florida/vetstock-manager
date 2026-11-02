package es.florida.vet.dto;

import es.florida.vet.entity.Firma;
import java.time.LocalDateTime;

public record FirmaResponse(LocalDateTime fecha, String veterinarioNombre, String veterinarioUsername,
                            String tipoFirma, String nombreArchivo, String contenidoPedido,
                            boolean tieneImagen, String firmanteNombre, String firmanteUsername,
                            String firmanteRol, String numeroColegiado) {
    public static FirmaResponse desde(Firma f) {
        var responsable = f.getPedido().getVeterinario().getUsuario();
        return new FirmaResponse(f.getFechaFirma(), responsable.getNombreCompleto(), responsable.getUsername(),
                f.getTipoFirma().name(), f.getNombreArchivo(), f.getContenidoPedido(),
                f.getImagen() != null && f.getImagen().length > 0,
                f.getUsuario().getNombreCompleto(), f.getUsuario().getUsername(),
                f.getUsuario().getRol().name(), f.getNumeroColegiado());
    }
}
