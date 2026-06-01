package com.zoick.farmmarket.domain.order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contains the bulk expiry query-> this is what the scheduler calls every 2 minutes
 * Also has the active reservation lookup per user and batch used by the lazy expiry check before every
 * new reservation attempt
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderId(UUID orderId);
    Optional<Reservation> findByUserIdAndBatchIdAndStatus(
            UUID userId, UUID batchId, ReservationStatus status);
    List<Reservation> findAllByStatus(ReservationStatus status);
    List<Reservation> findAllByUserIdAndStatusAndExpiresAtBefore(UUID userId, ReservationStatus status,
                                                                 LocalDateTime now);
    //finder for scheduler-> service layer loops and returns stock per reservation
    List<Reservation> findAllByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime now);

    // Bulk expiry — called by scheduler every 2 minutes
    @Modifying(clearAutomatically = true)
    @Query("""
    UPDATE Reservation r
    SET r.status = :expired
    WHERE r.expiresAt < :now
    AND r.status = :active
    AND r.order.id = :orderId
    """)
    int expireSingleReservation(@Param("orderId") UUID orderId,
            @Param("now") LocalDateTime now, @Param("active") ReservationStatus active,
                                @Param("expired") ReservationStatus expired);
    // Fetch all active reservations on a batch — used when admin cancels a batch
    List<Reservation> findAllByBatchIdAndStatus(UUID batchId, ReservationStatus status);
    //bulk fetch
    @Query("SELECT r FROM Reservation r JOIN FETCH r.order o WHERE o.id IN :orderIds")
    List<Reservation> findAllByOrderIdIn(@Param("orderIds") List<UUID> orderIds);
}