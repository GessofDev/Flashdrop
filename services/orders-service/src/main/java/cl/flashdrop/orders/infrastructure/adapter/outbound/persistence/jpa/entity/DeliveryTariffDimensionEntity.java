package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "delivery_tariff_dimension_ranges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryTariffDimensionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_dimension", nullable = false)
    private int minDimension;

    @Column(name = "max_dimension")
    private Integer maxDimension;

    @Column(name = "vehicle", nullable = false, length = 10)
    private String vehicle;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
