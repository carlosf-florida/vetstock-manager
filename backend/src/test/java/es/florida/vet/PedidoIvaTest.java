package es.florida.vet;

import es.florida.vet.entity.Pedido;
import es.florida.vet.entity.Producto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PedidoIvaTest {
    private void linea(Pedido pedido, String precio, int cantidad) {
        Producto producto = mock(Producto.class);
        when(producto.getPrecio()).thenReturn(new BigDecimal(precio));
        pedido.agregarLinea(producto, cantidad);
    }

    @Test
    void dosUnidadesIncluyenIvaEnElTotalACobrar() {
        Pedido pedido = new Pedido();
        linea(pedido, "14.50", 2);
        assertEquals(new BigDecimal("4.00"), pedido.getIvaPorcentaje());
        assertEquals(new BigDecimal("29.00"), pedido.getBaseImponible());
        assertEquals(new BigDecimal("1.16"), pedido.getImporteIva());
        assertEquals(new BigDecimal("30.16"), pedido.getTotal());
        assertEquals("EUR", pedido.getMoneda());
    }

    @Test
    void redondeaElIvaPorLineaAntesDeSumarlo() {
        Pedido pedido = new Pedido();
        linea(pedido, "0.13", 1);
        linea(pedido, "0.13", 1);
        assertEquals(new BigDecimal("0.26"), pedido.getBaseImponible());
        assertEquals(new BigDecimal("0.02"), pedido.getImporteIva());
        assertEquals(new BigDecimal("0.28"), pedido.getTotal());
    }

    @Test
    void cantidadPerteneceALaMismaLineaParaElRedondeo() {
        Pedido pedido = new Pedido();
        linea(pedido, "0.13", 3);
        assertEquals(new BigDecimal("0.39"), pedido.getBaseImponible());
        assertEquals(new BigDecimal("0.02"), pedido.getImporteIva());
        assertEquals(new BigDecimal("0.41"), pedido.getTotal());
    }

    @Test
    void historicoCargadoConTasaCeroConservaElImporteOriginal() {
        Pedido pedido = new Pedido();
        linea(pedido, "14.50", 2);
        ReflectionTestUtils.setField(pedido, "ivaPorcentaje", new BigDecimal("0.00"));
        assertEquals(new BigDecimal("0.00"), pedido.getImporteIva());
        assertEquals(new BigDecimal("29.00"), pedido.getTotal());
        assertEquals(new BigDecimal("14.50"), pedido.getLineas().get(0).getPrecioUnitario());
    }

    @Test
    void pedidoVacioNoInventaImportes() {
        Pedido pedido = new Pedido();
        assertEquals(new BigDecimal("0.00"), pedido.getBaseImponible());
        assertEquals(new BigDecimal("0.00"), pedido.getImporteIva());
        assertEquals(new BigDecimal("0.00"), pedido.getTotal());
    }
}
