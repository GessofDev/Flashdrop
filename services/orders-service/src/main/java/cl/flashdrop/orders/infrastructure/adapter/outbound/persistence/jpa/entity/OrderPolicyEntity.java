package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "order_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code null} = politica global. */
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "payment_expiration_minutes")
    private Integer paymentExpirationMinutes;

    @Column(name = "store_response_minutes")
    private Integer storeResponseMinutes;

    @Column(name = "customer_cancel_window_minutes")
    private Integer customerCancelWindowMinutes;

    @Column(name = "courier_wait_minutes")
    private Integer courierWaitMinutes;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
