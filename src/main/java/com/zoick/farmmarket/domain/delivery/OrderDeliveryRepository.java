package com.zoick.farmmarket.domain.delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * simple-> one delivery per order. The only custom query needed is lookup by order ID.
 */
@Repository
public interface OrderDeliveryRepository extends JpaRepository<OrderDelivery, UUID> {
    Optional<OrderDelivery> findByOrderId(UUID orderId);
    //finds deliveries fulfilled more than 24 hours ago and not yet confirmed by customer
    @Query("""
        SELECT od FROM OrderDelivery od
        JOIN FETCH od.order o
        WHERE od.fulfilledAt IS NOT NULL
        AND od.fulfilledAt < :cutoff
        AND od.customerConfirmedAt IS NULL
        AND od.autoConfirmed = false
        AND o.status = 'FULFILLED'
        """)
    List<UUID> findPendingConfirmationBefore(@Param("cutoff") LocalDateTime cutoff);
    @Query("""
    SELECT od FROM OrderDelivery od
    JOIN FETCH od.order o
    JOIN FETCH o.user
    WHERE od.id = :id
    """)
    Optional<OrderDelivery> findByIdWithOrderAndUser(@Param("id") UUID id);
    //bulk fetch
    @Query("SELECT od FROM OrderDelivery od JOIN FETCH od.order o WHERE o.id IN :orderIds")
    List<OrderDelivery> findAllByOrderIdIn(@Param("orderIds") List<UUID> orderIds);
}