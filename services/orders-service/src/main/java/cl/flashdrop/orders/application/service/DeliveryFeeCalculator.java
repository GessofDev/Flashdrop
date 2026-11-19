package cl.flashdrop.orders.application.service;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryQuote;
import cl.flashdrop.orders.domain.model.DeliveryTariff;
import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.DeliveryTariffRepositoryPort;
import cl.flashdrop.orders.domain.port.DistanceCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calcula el costo de envio de un pedido (F8) a partir de la tarifa que define el administrador.
 *
 * <p>La cobertura siempre se valida antes de cotizar o crear pedidos. Sin rangos de tarifa se
 * conserva el envio de respaldo, pero nunca se omiten las coordenadas ni el radio del local.</p>
 */
@Service
@Profile({"postgres", "default"})
public class DeliveryFeeCalculator {

    /** Un producto del pedido con su cantidad. */
    public record Line(ProductInfo product, int quantity) {
    }

    private final DeliveryTariffRepositoryPort tariffs;
    private final DistanceCalculator distances;
    private final BigDecimal fallbackFee;
    private final int defaultProductDimension;

    public DeliveryFeeCalculator(DeliveryTariffRepositoryPort tariffs,
                                 DistanceCalculator distances,
                                 @Value("${orders.delivery-fee:2500}") BigDecimal fallbackFee,
                                 @Value("${orders.default-product-dimension:1}") int defaultProductDimension) {
        this.tariffs = tariffs;
        this.distances = distances;
        this.fallbackFee = fallbackFee;
        this.defaultProductDimension = defaultProductDimension;
    }

    @Transactional(readOnly = true)
    public DeliveryQuote calculate(List<Line> lines, RestaurantInfo restaurant,
                                   BigDecimal destinationLatitude, BigDecimal destinationLongitude) {
        if ((destinationLatitude == null) != (destinationLongitude == null)) {
            throw new OrderDomainException("La latitud y la longitud del destino deben enviarse juntas");
        }
        if (destinationLatitude == null) {
            throw new OrderDomainException("Selecciona una direccion con ubicacion para validar la cobertura");
        }
        if (restaurant == null || restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
            throw new OrderDomainException("El local no tiene ubicacion configurada: no se puede calcular el envio");
        }
        validateCoordinates(destinationLatitude, destinationLongitude);
        BigDecimal radius = restaurant.getDeliveryRadiusKm();
        if (radius == null || radius.compareTo(BigDecimal.ONE) < 0
                || radius.compareTo(BigDecimal.valueOf(50)) > 0) {
            throw new OrderDomainException("El local no tiene radio de despacho configurado");
        }
        BigDecimal km = distances.kmBetween(restaurant.getLatitude(), restaurant.getLongitude(),
                destinationLatitude, destinationLongitude);
        if (km.compareTo(radius) > 0) {
            throw new OrderDomainException("El local no realiza entregas en esta direccion: fuera de su radio de despacho");
        }
        DeliveryTariff tariff = tariffs.find();
        if (tariff.isEmpty()) {
            return DeliveryQuote.fallback(fallbackFee, km);
        }
        return tariff.quote(orderDimension(lines), km);
    }

    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new OrderDomainException("Las coordenadas del destino no son validas");
        }
    }

    /** Dimension del pedido: suma de cantidad x dimension de cada producto (sin dato, la por defecto). */
    int orderDimension(List<Line> lines) {
        return lines.stream()
                .mapToInt(l -> Math.max(1, l.quantity())
                        * (l.product().getDimension() != null ? l.product().getDimension() : defaultProductDimension))
                .sum();
    }
}
