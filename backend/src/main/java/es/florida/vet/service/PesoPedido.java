package es.florida.vet.service;

import es.florida.vet.entity.LineaPedido;
import es.florida.vet.entity.Pedido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Peso del pedido y número de bultos que exige la regla de negocio (regla fija, sin IA):
 *   peso total = suma de (peso por unidad × cantidad) de cada línea
 *   bultos     = peso total / 25 kg redondeado HACIA ARRIBA, como mínimo 1
 * Se calcula con BigDecimal (decimal exacto), así 25 kg da 1 bulto y 25,001 kg da 2.
 * Si a algún producto le falta el peso, el cálculo queda INCOMPLETO: el peso desconocido
 * no se trata como 0 y el número de bultos queda sin calcular (null).
 *
 * @param pesoTotalKg       suma de las líneas con peso conocido (si está incompleto es un mínimo)
 * @param bultosCalculados  bultos que exige la regla; null si el cálculo está incompleto
 * @param productosSinPeso  nombres de los productos del pedido que no tienen peso
 */
public record PesoPedido(BigDecimal pesoTotalKg, Integer bultosCalculados, List<String> productosSinPeso) {

    /** Peso máximo que admite un bulto. */
    public static final BigDecimal KG_POR_BULTO = new BigDecimal("25");

    public static PesoPedido de(Pedido pedido) {
        BigDecimal total = BigDecimal.ZERO;
        List<String> sinPeso = new ArrayList<>();
        for (LineaPedido linea : pedido.getLineas()) {
            BigDecimal pesoUnidad = linea.getProducto().getPesoUnitarioKg();
            if (pesoUnidad == null) {
                sinPeso.add(linea.getProducto().getNombre());
            } else {
                // Peso de la línea = peso por UNIDAD × cantidad (no se confunde con el de la línea)
                total = total.add(pesoUnidad.multiply(BigDecimal.valueOf(linea.getCantidad())));
            }
        }
        Integer bultos = sinPeso.isEmpty() ? bultosParaPeso(total) : null;
        return new PesoPedido(total.setScale(3, RoundingMode.UNNECESSARY), bultos, List.copyOf(sinPeso));
    }

    /** Bultos para un peso conocido: techo(peso / 25), mínimo 1. */
    public static int bultosParaPeso(BigDecimal pesoKg) {
        int bultos = pesoKg.divide(KG_POR_BULTO, 0, RoundingMode.CEILING).intValue();
        return Math.max(1, bultos);
    }

    public boolean completo() {
        return bultosCalculados != null;
    }
}
