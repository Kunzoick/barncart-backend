package com.zoick.farmmarket.domain.produce;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "harvest_batch")
@Access(AccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
//The core inventory unit, Reference Produce. Contains the live stock number that gets atomically decremented
//during reservations.
public class HarvestBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produce_id", nullable = false)
    private Produce produce;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityOriginal;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityAvailable;
    @Column(nullable = false)
    private LocalDate harvestedAt;
    @Column(nullable = false)
    private LocalDate expiryDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status= BatchStatus.ACTIVE;
    @Column(columnDefinition = "TEXT")
    private String notes;
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