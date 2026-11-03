package es.florida.vet.repository;

import es.florida.vet.entity.Bulto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BultoRepository extends JpaRepository<Bulto, Long> {
    List<Bulto> findByPedidoId(Long pedidoId);
    List<Bulto> findByPaletId(Long paletId);
    List<Bulto> findAllByOrderByIdDesc();

    boolean existsByIdentificador(String identificador);

    long countByPedidoId(Long pedidoId);

    /** Recuento de bultos por pedido en UNA consulta (para el listado, sin una consulta por fila). */
    @org.springframework.data.jpa.repository.Query("SELECT b.pedido.id, COUNT(b) FROM Bulto b GROUP BY b.pedido.id")
    List<Object[]> contarPorPedido();
}
