package com.zoick.farmmarket.domain.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliverySlotRepository extends JpaRepository<DeliverySlot, UUID> {

    List<DeliverySlot> findAllBySlotDate(LocalDate slotDate);

    Optional<DeliverySlot> findBySlotDateAndSlotType(LocalDate slotDate, SlotType slotType);

    // All slots across a date range — used by admin view (includes full slots)
    List<DeliverySlot> findAllBySlotDateBetween(LocalDate from, LocalDate to);

    // Available slots only — filters at DB level, not in Java
    @Query("""
        SELECT s FROM DeliverySlot s
        WHERE s.slotDate = :date
        AND s.bookedCount < s.capacity
        """)
    List<DeliverySlot> findAvailableBySlotDate(@Param("date") LocalDate date);

    // Available slots across a date range — ordered for calendar display
    @Query("""
        SELECT s FROM DeliverySlot s
        WHERE s.slotDate BETWEEN :from AND :to
        AND s.bookedCount < s.capacity
        ORDER BY s.slotDate, s.slotType
        """)
    List<DeliverySlot> findAvailableBySlotDateBetween(@Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // Atomic increment — returns 1 if capacity not exceeded, 0 if full
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE DeliverySlot d
        SET d.bookedCount = d.bookedCount + 1
        WHERE d.id = :slotId
        AND d.bookedCount < d.capacity
        """)
    int incrementBookedCount(@Param("slotId") UUID slotId);

    // Decrement — used when order is cancelled after slot was booked
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE DeliverySlot d
        SET d.bookedCount = d.bookedCount - 1
        WHERE d.id = :slotId
        AND d.bookedCount > 0
        """)
    int decrementBookedCount(@Param("slotId") UUID slotId);
}