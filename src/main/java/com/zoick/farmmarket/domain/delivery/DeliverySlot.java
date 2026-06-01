package com.zoick.farmmarket.domain.delivery;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_slot", uniqueConstraints = @UniqueConstraint(
        name = "uq_slot_per_date", columnNames = {"slot_date", "slot_type"}
))
@Access(AccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
//Holds capacity and booking count. bookedCount is incremented atomically in DeliverySlotService
public class DeliverySlot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;
    @Column(nullable = false)
    private LocalDate slotDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SlotType slotType;

    @Column(nullable = false)
    private int capacity = 10;

    @Column(nullable = false)
    private int bookedCount = 0;

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