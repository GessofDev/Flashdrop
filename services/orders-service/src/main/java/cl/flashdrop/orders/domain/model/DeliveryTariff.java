package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Tarifa de envio parametrizable (F8): dos tablas que define el administrador.
 *
 * <ul>
 *   <li><b>Dimension:</b> rangos de la dimension total del pedido (enteros, ambos extremos
 *       incluidos). Cada rango fija el vehiculo y su precio.</li>
 *   <li><b>Distancia:</b> rangos de km (desde incluido, hasta excluido) con su precio.</li>
 * </ul>
 *
 * <p>El envio es la suma de ambos costos. Las tablas deben cubrir todos los valores: parten en 0,
 * no se solapan, no dejan huecos y el ultimo rango queda abierto hacia arriba. Si ambas estan
 * vacias no hay tarifa y se cobra el envio de respaldo.</p>
 */
public record DeliveryTariff(List<DimensionRange> dimensionRanges, List<DistanceRange> distanceRanges) {

    /** {@code maxDimension == null} = sin tope. */
    public record DimensionRange(int minDimension, Integer maxDimension, Vehicle vehicle, BigDecimal price) {
        boolean contains(int dimension) {
            return dimension >= minDimension && (maxDimension == null || dimension <= maxDimension);
        }
    }

    /** {@code maxKm == null} = sin tope. El extremo superior no se incluye. */
    public record DistanceRange(BigDecimal minKm, BigDecimal maxKm, BigDecimal price) {
        boolean contains(BigDecimal km) {
            return km.compareTo(minKm) >= 0 && (maxKm == null || km.compareTo(maxKm) < 0);
        }
    }

    public DeliveryTariff {
        dimensionRanges = sortedCopy(dimensionRanges, Comparator.comparingInt(DimensionRange::minDimension));
        distanceRanges = sortedCopy(distanceRanges, Comparator.comparing(DistanceRange::minKm));
    }

    private static <T> List<T> sortedCopy(List<T> source, Comparator<T> order) {
        return source == null ? List.of() : source.stream().sorted(order).toList();
    }

    public static DeliveryTariff empty() {
        return new DeliveryTariff(List.of(), List.of());
    }

    /** Sin rangos cargados: no hay tarifa y se usa el envio de respaldo. */
    public boolean isEmpty() {
        return dimensionRanges.isEmpty() && distanceRanges.isEmpty();
    }

    public void validate() {
        if (isEmpty()) {
            return;
        }
        if (dimensionRanges.isEmpty() || distanceRanges.isEmpty()) {
            throw new OrderDomainException("La tarifa debe definir rangos de dimension y de distancia");
        }
        validateDimensions();
        validateDistances();
    }

    private void validateDimensions() {
        int expectedMin = 0;
        for (int i = 0; i < dimensionRanges.size(); i++) {
            DimensionRange range = dimensionRanges.get(i);
            boolean last = i == dimensionRanges.size() - 1;
            if (range.vehicle() == null) {
                throw new OrderDomainException("Cada rango de dimension debe indicar el vehiculo");
            }
            requirePrice(range.price(), "dimension");
            if (range.minDimension() != expectedMin) {
                throw new OrderDomainException("Los rangos de dimension deben partir en 0 y no dejar huecos ni solaparse: "
                        + "se esperaba un rango desde " + expectedMin);
            }
            if (last) {
                if (range.maxDimension() != null) {
                    throw new OrderDomainException("El ultimo rango de dimension debe quedar abierto (sin maximo)");
                }
            } else {
                if (range.maxDimension() == null || range.maxDimension() < range.minDimension()) {
                    throw new OrderDomainException("Solo el ultimo rango de dimension puede quedar abierto y el maximo "
                            + "no puede ser menor al minimo");
                }
                expectedMin = range.maxDimension() + 1;
            }
        }
    }

    private void validateDistances() {
        BigDecimal expectedMin = BigDecimal.ZERO;
        for (int i = 0; i < distanceRanges.size(); i++) {
            DistanceRange range = distanceRanges.get(i);
            boolean last = i == distanceRanges.size() - 1;
            requirePrice(range.price(), "distancia");
            if (range.minKm() == null || range.minKm().compareTo(expectedMin) != 0) {
                throw new OrderDomainException("Los rangos de distancia deben partir en 0 km y no dejar huecos ni solaparse: "
                        + "se esperaba un rango desde " + expectedMin.toPlainString() + " km");
            }
            if (last) {
                if (range.maxKm() != null) {
                    throw new OrderDomainException("El ultimo rango de distancia debe quedar abierto (sin maximo)");
                }
            } else {
                if (range.maxKm() == null || range.maxKm().compareTo(range.minKm()) <= 0) {
                    throw new OrderDomainException("Solo el ultimo rango de distancia puede quedar abierto y el maximo "
                            + "debe ser mayor al minimo");
                }
                expectedMin = range.maxKm();
            }
        }
    }

    private static void requirePrice(BigDecimal price, String table) {
        if (price == null || price.signum() < 0) {
            throw new OrderDomainException("El precio de cada rango de " + table + " no puede ser negativo");
        }
    }

    /**
     * Calcula el envio: precio del rango de dimension + precio del rango de distancia.
     * La tarifa debe estar cargada y validada.
     */
    public DeliveryQuote quote(int orderDimension, BigDecimal distanceKm) {
        DimensionRange dimension = dimensionRanges.stream().filter(r -> r.contains(orderDimension)).findFirst()
                .orElseThrow(() -> new OrderDomainException("No hay un rango de dimension para el pedido"));
        DistanceRange distance = distanceRanges.stream().filter(r -> r.contains(distanceKm)).findFirst()
                .orElseThrow(() -> new OrderDomainException("No hay un rango de distancia para el destino"));
        return new DeliveryQuote(dimension.price().add(distance.price()), dimension.vehicle(), orderDimension,
                distanceKm, dimension.price(), distance.price(), false);
    }
}
