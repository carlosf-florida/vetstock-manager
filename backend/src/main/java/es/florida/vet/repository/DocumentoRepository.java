package es.florida.vet.repository;

import es.florida.vet.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    List<Documento> findByPedidoId(Long pedidoId);
}
