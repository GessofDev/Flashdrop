package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "order_delivery_operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDeliveryOperationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "operation_type", nullable = false)
    private String operationType;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "target_status")
    private String targetStatus;

    @Column(name = "previous_delivery_id")
    private Long previousDeliveryId;

    @Column(name = "resulting_delivery_id")
    private Long resultingDeliveryId;

    @Column(name = "previous_status")
    private String previousStatus;

    @Column(name = "resulting_status")
    private String resultingStatus;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
