package com.zoick.farmmarket.domain.payment;

import com.zoick.farmmarket.domain.order.Order;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Access(AccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
//Tracks the full payment lifecycle including refunds, This is the webhook idempotency guard.
//if stripe sends the same event twice the second insert fails and return 200 immediately without reprocessing
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(nullable = false, length = 20)
    private String provider;
    @Column(nullable = false, length = 255)
    private String providerPaymentId;
    @Column(unique = true, length = 255)
    private String providerEventId;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(precision = 10, scale = 2)
    private BigDecimal refundAmount;
    @Column(columnDefinition = "TEXT")
    private String refundReason;
    private LocalDateTime refundedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(length = 255)
    private String clientSecret;

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