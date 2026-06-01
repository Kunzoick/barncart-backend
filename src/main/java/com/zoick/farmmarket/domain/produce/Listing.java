package com.zoick.farmmarket.domain.produce;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "listing")
@Access(AccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
//One listing per batch. Holds pricing data both retail and bulk
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, unique = true)
    private HarvestBatch batch;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal retailPrice;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal bulkPrice;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal minBulkQuantity;
    @Column(nullable = false, length = 3)
    private String currency = "CAD";
    @Column(nullable = false)
    private int lowStockThresholdPct = 25;
    @Column(precision = 10, scale = 2)
    private BigDecimal bagWeightKg;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
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