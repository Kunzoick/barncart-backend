package com.zoick.farmmarket.domain.order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
//for admin fulfillment view cannot display what was in an order, and the order detail endpoint cannot return line items
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    @Query("""
        SELECT oi FROM OrderItem oi
        JOIN FETCH oi.listing l
        JOIN FETCH oi.batch b
        JOIN FETCH b.produce p
        WHERE oi.order.id = :orderId
        """)
    List<OrderItem> findAllByOrderIdWithDetails(@Param("orderId") UUID orderId);
    // Bulk fetch — JOIN FETCH order so getOrder().getId() is safe in Java groupBy
    @Query("""
        SELECT oi FROM OrderItem oi
        JOIN FETCH oi.order o
        JOIN FETCH oi.listing l
        JOIN FETCH oi.batch b
        JOIN FETCH b.produce p
        WHERE o.id IN :orderIds
        """)
    List<OrderItem> findAllByOrderIdsWithDetails(@Param("orderIds") List<UUID> orderIds);

    @Query("""
        SELECT b.produce.name,
               SUM(oi.quantity),
               SUM(oi.priceAtPurchase * oi.quantity)
        FROM OrderItem oi
        JOIN oi.batch b
        JOIN oi.order o
        WHERE o.status IN ('PAID', 'FULFILLED', 'DELIVERED')
        GROUP BY b.produce.name
        ORDER BY SUM(oi.quantity) DESC
        """)
    List<Object[]> findTopProducts();
}
