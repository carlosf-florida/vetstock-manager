package es.florida.vet;

import es.florida.vet.entity.Pedido;
import es.florida.vet.entity.Producto;
import es.florida.vet.service.PesoPedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regla de bultos: techo(peso total / 25 kg), mínimo 1, con decimales exactos. */
class PesoPedidoTest {

    private static Producto producto(String nombre, String pesoKg) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setPrecio(BigDecimal.ONE);
        p.setPesoUnitarioKg(pesoKg == null ? null : new BigDecimal(pesoKg));
        return p;
    }

    @ParameterizedTest(name = "{0} kg → {1} bultos")
    @CsvSource({
            "10, 1", "24.99, 1", "25, 1", "25.000, 1", "25.001, 2", "25.01, 2", "25.1, 2",
            "49.99, 2", "50, 2", "50.01, 3", "50.1, 3", "75, 3", "75.001, 4", "0.001, 1"
    })
    @DisplayName("límites de 25, 50 y 75 kg")
    void limites(String pesoKg, int bultosEsperados) {
        Pedido pedido = new Pedido();
        pedido.agregarLinea(producto("Caja", pesoKg), 1);
        PesoPedido peso = PesoPedido.de(pedido);
        assertTrue(peso.completo());
        assertEquals(bultosEsperados, peso.bultosCalculados());
    }

    @Test
    @DisplayName("peso por UNIDAD × cantidad, varias líneas")
    void cantidadesMultiples() {
        Pedido pedido = new Pedido();
        pedido.agregarLinea(producto("Saco", "12.500"), 2);   // 25 kg exactos
        assertEquals(1, PesoPedido.de(pedido).bultosCalculados());
        assertEquals(new BigDecimal("25.000"), PesoPedido.de(pedido).pesoTotalKg());

        pedido.agregarLinea(producto("Guantes", "0.600"), 3); // + 1,8 kg = 26,8 kg
        PesoPedido peso = PesoPedido.de(pedido);
        assertEquals(new BigDecimal("26.800"), peso.pesoTotalKg());
        assertEquals(2, peso.bultosCalculados());
    }

    @Test
    @DisplayName("gramos sueltos no se pierden por redondeo: 24,999 + 0,001 = 25 kg exactos")
    void sinErroresDeComaFlotante() {
        Pedido pedido = new Pedido();
        pedido.agregarLinea(producto("A", "24.999"), 1);
        pedido.agregarLinea(producto("B", "0.001"), 1);
        assertEquals(1, PesoPedido.de(pedido).bultosCalculados());
        // 8,333 × 3 = 24,999 exactos (en coma flotante podría quedar 24,999000000000002)
        Pedido otro = new Pedido();
        otro.agregarLinea(producto("C", "8.333"), 3);   // 24,999 kg
        assertEquals(1, PesoPedido.de(otro).bultosCalculados());
    }

    @Test
    @DisplayName("una unidad de más de 25 kg se señala, pero el cálculo no se inventa nada más")
    void unidadSobreElLimite() {
        Pedido pedido = new Pedido();
        pedido.agregarLinea(producto("Bidón", "30.000"), 1);
        pedido.agregarLinea(producto("Saco", "25.000"), 1);
        PesoPedido peso = PesoPedido.de(pedido);
        assertEquals(List.of("Bidón"), peso.productosSobreLimite());
        assertEquals(3, peso.bultosCalculados()); // 55 kg
    }

    @Test
    @DisplayName("producto sin peso: cálculo incompleto, sin bultos y sin tratarlo como 0")
    void productoSinPeso() {
        Pedido pedido = new Pedido();
        pedido.agregarLinea(producto("Saco", "12.500"), 1);
        pedido.agregarLinea(producto("Vacuna", null), 4);
        PesoPedido peso = PesoPedido.de(pedido);
        assertFalse(peso.completo());
        assertNull(peso.bultosCalculados());
        assertEquals(List.of("Vacuna"), peso.productosSinPeso());
        assertEquals(new BigDecimal("12.500"), peso.pesoTotalKg()); // solo lo conocido (es un mínimo)
    }
}
