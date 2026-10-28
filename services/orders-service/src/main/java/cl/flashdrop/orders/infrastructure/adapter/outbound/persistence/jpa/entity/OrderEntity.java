package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "delivery_id")
    private Long deliveryId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder.Default
    @Column(name = "reception_mode", nullable = false)
    private String receptionMode = "NORMAL";

    @Builder.Default
    @Column(name = "delivery_code_version", nullable = false)
    private int deliveryCodeVersion = 1;

    @Builder.Default
    @Column(name = "delivery_code_attempts", nullable = false)
    private int deliveryCodeAttempts = 0;

    @Column(name = "delivery_code_verified_at")
    private OffsetDateTime deliveryCodeVerifiedAt;
}
