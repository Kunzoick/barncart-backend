package com.zoick.farmmarket.domain.order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orders are queried by user for the customer order history endpoint and by status for admin management
 * The findByIdempotencyKey lookup is critical for idempotency
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Order> findAllByStatus(OrderStatus status);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    List<Order> findAllByOrderByCreatedAtDesc();

    @Query("SELECT o FROM Order o JOIN FETCH o.user WHERE o.id = :id")
    Optional<Order> findByIdWithUser(@Param("id") UUID id);
    @Query(value = "SELECT o FROM Order o ORDER BY o.createdAt DESC",
            countQuery = "SELECT COUNT(o) FROM Order o")
    Page<Order> findAllPagedOrderByCreatedAtDesc(Pageable pageable);

    //analytics
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status IN :statuses")
    BigDecimal sumRevenue(@Param("statuses") List<OrderStatus> statuses);
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<OrderStatus> statuses);
    @Query("SELECT AVG(o.totalAmount) FROM Order o WHERE o.status IN :statuses")
    BigDecimal avgTotalAmount(@Param("statuses") List<OrderStatus> statuses);
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupByStatus();
    @Query(value = """
        SELECT CONCAT(YEAR(created_at), '-W', LPAD(WEEK(created_at, 3), 2, '0')) as week_label,
               SUM(total_amount) as revenue
        FROM orders
        WHERE status IN ('PAID', 'FULFILLED', 'DELIVERED')
        AND created_at >= DATE_SUB(NOW(), INTERVAL 8 WEEK)
        GROUP BY YEAR(created_at), WEEK(created_at, 3)
        ORDER BY YEAR(created_at), WEEK(created_at, 3), CONCAT(YEAR(created_at), '-W', LPAD(WEEK(created_at, 3), 2, '0'))
        """, nativeQuery = true)
    List<Object[]> findWeeklyRevenue();
}