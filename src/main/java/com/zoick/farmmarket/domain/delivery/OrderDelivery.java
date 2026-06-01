package com.zoick.farmmarket.domain.delivery;

import com.zoick.farmmarket.domain.order.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_delivery")
@Access(AccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
//links an order to a delivery slot and stores the delivery address.
public class OrderDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_slot_id", nullable = false)
    private DeliverySlot deliverySlot;
    @Column(name = "address_line_1", nullable = false, length = 255)
    private String addressLine1;
    @Column(name = "address_line_2", length = 255)
    private String addressLine2;
    @Column(nullable = false, length = 100)
    private String city;
    @Column(nullable = false, length = 100)
    private String province;
    @Column(nullable = false, length = 20)
    private String postalCode;
    @Column(nullable = false, length = 3)
    private String country = "CA";
    @Column(columnDefinition = "TEXT")
    private String deliveryNotes;
    private LocalDateTime fulfilledAt;
    private LocalDateTime customerConfirmedAt;
    @Column(nullable = false)
    private boolean autoConfirmed = false;
    @Column(length = 255)
    private String disputeReason;
    private LocalDateTime disputeResolvedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}