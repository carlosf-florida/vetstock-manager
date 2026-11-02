package es.florida.vet.controller;

import es.florida.vet.dto.DocumentoResponse;
import es.florida.vet.entity.Documento;
import es.florida.vet.service.DocumentoService;
import es.florida.vet.util.AuthUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Documentos adjuntos. Descarga protegida: solo ADMIN o la clínica del pedido. */
@RestController
@RequestMapping("/api")
public class DocumentoController {

    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @PostMapping(value = "/pedidos/{pedidoId}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse subir(@PathVariable Long pedidoId,
                                   @RequestParam("archivo") MultipartFile archivo,
                                   @RequestParam("tipo") String tipo,
                                   Authentication auth) {
        return documentoService.subir(pedidoId, tipo, archivo, auth.getName(), AuthUtil.esAdmin(auth));
    }

    @GetMapping("/pedidos/{pedidoId}/documentos")
    public List<DocumentoResponse> listar(@PathVariable Long pedidoId, Authentication auth) {
        return documentoService.listarDePedido(pedidoId, auth.getName(), AuthUtil.esAdmin(auth));
    }

    @GetMapping("/documentos/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, Authentication auth) {
        Documento documento = documentoService.descargar(id, auth.getName(), AuthUtil.esAdmin(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + documento.getNombreArchivo() + "\"")
                .body(documento.getContenido());
    }
}
