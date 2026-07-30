package cl.flashdrop.orders.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Información de un producto obtenida desde el Catálogo.
 *
 * En esta fase inicial, el dato es consultado directamente desde la base de datos
 * compartida a través del adaptador {@code CatalogJpaAdapter}.
 *
 * En el futuro, cuando catalog-service sea un servicio independiente,
 * bastará con reemplazar el adaptador por un cliente HTTP/gRPC
 * sin modificar ninguna regla de negocio ni caso de uso.
 */
@Getter
@Builder
public class ProductInfo {

    private final UUID id;
    private final UUID restaurantId;
    private final String name;
    private final String description;
    private final String image;
    private final BigDecimal price;
    private final boolean available;
}
