package es.florida.vet.repository;

import es.florida.vet.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findAllByOrderByNombreAsc();

    boolean existsByCodigo(String codigo);

    /**
     * RESERVA ATÓMICA: incrementa stockReservado solo si queda stock disponible.
     * La condición está en la propia consulta SQL, de modo que aunque dos
     * peticiones lleguen a la vez, la base de datos las ejecuta una detrás de
     * otra y solo una de ellas puede reservar las últimas unidades.
     * Devuelve 1 si reservó y 0 si no había stock suficiente.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.stockReservado = p.stockReservado + :cantidad " +
           "WHERE p.id = :id AND p.activo = true AND (p.stockActual - p.stockReservado) >= :cantidad")
    int reservar(@Param("id") Long id, @Param("cantidad") int cantidad);

    /** Libera una reserva (al cancelar). Devuelve 1 si se liberó. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.stockReservado = p.stockReservado - :cantidad " +
           "WHERE p.id = :id AND p.stockReservado >= :cantidad")
    int liberarReserva(@Param("id") Long id, @Param("cantidad") int cantidad);

    /** Descuenta el stock físico y libera la reserva, una sola vez (al expedir). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.stockActual = p.stockActual - :cantidad, " +
           "p.stockReservado = p.stockReservado - :cantidad " +
           "WHERE p.id = :id AND p.stockActual >= :cantidad AND p.stockReservado >= :cantidad")
    int confirmarSalida(@Param("id") Long id, @Param("cantidad") int cantidad);
}
