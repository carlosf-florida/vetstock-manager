package es.florida.vet.service;

import es.florida.vet.dto.DocumentoResponse;
import es.florida.vet.entity.Documento;
import es.florida.vet.entity.Pedido;
import es.florida.vet.entity.Usuario;
import es.florida.vet.enums.TipoDocumento;
import es.florida.vet.exception.AccesoDenegadoException;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.repository.DocumentoRepository;
import es.florida.vet.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Documentos adjuntos (PDF hasta 10 MB). El contenido se guarda en la base
 * de datos y SOLO se sirve a usuarios autorizados: nunca hay enlaces públicos.
 * Un "documento de factura" aquí es de demostración, no una factura fiscal válida.
 */
@Service
public class DocumentoService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;   // 10 MB

    private final DocumentoRepository documentoRepository;
    private final PedidoRepository pedidoRepository;
    private final SesionService sesionService;

    public DocumentoService(DocumentoRepository documentoRepository,
                            PedidoRepository pedidoRepository,
                            SesionService sesionService) {
        this.documentoRepository = documentoRepository;
        this.pedidoRepository = pedidoRepository;
        this.sesionService = sesionService;
    }

    @Transactional
    public DocumentoResponse subir(Long pedidoId, String tipo, MultipartFile archivo,
                                   String username, boolean esAdmin) {
        if (!esAdmin) {
            throw new AccesoDenegadoException("Solo administración puede adjuntar documentos");
        }
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido no existe"));

        TipoDocumento tipoDocumento;
        try {
            tipoDocumento = TipoDocumento.valueOf(tipo);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ReglaNegocioException("El tipo de documento no es válido");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException("No se ha recibido ningún archivo");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new ReglaNegocioException("El archivo supera los 10 MB permitidos");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer el archivo");
        }
        // Validación en el servidor: debe ser un PDF real (cabecera %PDF)
        if (contenido.length < 5 || !new String(contenido, 0, 5).equals("%PDF-")) {
            throw new ReglaNegocioException("El archivo debe ser un PDF");
        }
        Usuario usuario = sesionService.usuarioActual(username);
        Documento documento = new Documento(pedido, tipoDocumento,
                limpiarNombre(archivo.getOriginalFilename()), contenido, usuario);
        return DocumentoResponse.desde(documentoRepository.save(documento));
    }

    @Transactional(readOnly = true)
    public List<DocumentoResponse> listarDePedido(Long pedidoId, String username, boolean esAdmin) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido no existe"));
        comprobarAcceso(pedido, username, esAdmin);
        return documentoRepository.findByPedidoId(pedidoId).stream()
                .map(DocumentoResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public Documento descargar(Long documentoId, String username, boolean esAdmin) {
        Documento documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El documento no existe"));
        comprobarAcceso(documento.getPedido(), username, esAdmin);
        return documento;
    }

    private void comprobarAcceso(Pedido pedido, String username, boolean esAdmin) {
        if (esAdmin) {
            return;
        }
        var usuario = sesionService.usuarioActual(username);
        if (!sesionService.puedeAccederAClinica(usuario, false, pedido.getClinica().getId())) {
            throw new AccesoDenegadoException("No tienes permiso sobre este documento");
        }
    }

    private String limpiarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "documento.pdf";
        }
        return nombre.replaceAll("[^a-zA-Z0-9._\\- áéíóúÁÉÍÓÚñÑ]", "_");
    }
}
