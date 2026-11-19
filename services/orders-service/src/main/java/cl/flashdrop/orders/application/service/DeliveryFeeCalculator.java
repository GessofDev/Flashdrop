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
 * <p>Reglas de respaldo: sin rangos cargados, o con un destino sin coordenadas (pedidos de apps que
 * aun no las envian), se cobra el envio de {@code orders.delivery-fee}. Si hay tarifa y destino pero
 * el local no tiene pin, se rechaza con un error claro: no se inventa un precio.</p>
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
            return DeliveryQuote.fallback(fallbackFee, null);
        }
        DeliveryTariff tariff = tariffs.find();
        if (tariff.isEmpty()) {
            return DeliveryQuote.fallback(fallbackFee, null);
        }
        if (restaurant == null || restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
            throw new OrderDomainException("El local no tiene ubicacion configurada: no se puede calcular el envio");
        }
        BigDecimal km = distances.kmBetween(restaurant.getLatitude(), restaurant.getLongitude(),
                destinationLatitude, destinationLongitude);
        return tariff.quote(orderDimension(lines), km);
    }

    /** Dimension del pedido: suma de cantidad x dimension de cada producto (sin dato, la por defecto). */
    int orderDimension(List<Line> lines) {
        return lines.stream()
                .mapToInt(l -> Math.max(1, l.quantity())
                        * (l.product().getDimension() != null ? l.product().getDimension() : defaultProductDimension))
                .sum();
    }
}
