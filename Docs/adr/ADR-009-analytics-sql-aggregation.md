# ADR-009 — Analytics: Pure SQL Aggregation, Never Load Full Table

## Status
Accepted

## Context
The admin analytics endpoint originally called `orderRepository.findAllByOrderByCreatedAtDesc()` — loading every order into the Java heap to compute revenue totals, counts, and averages in application code. With 10,000 orders this causes severe memory pressure. With 100,000 orders it causes OOM kills, particularly critical on a 512MB Render free tier instance.

## Decision
`AnalyticsService` uses five dedicated SQL queries. All aggregation is done in the database — no full table load is ever performed.

```java
// All computed in DB — no entities loaded into heap
BigDecimal totalRevenue = orderRepository.sumRevenue(paidStatuses);
long totalOrders = orderRepository.countByStatusIn(paidStatuses);
BigDecimal avgOrder = orderRepository.avgTotalAmount(paidStatuses);
List<Object[]> byStatus = orderRepository.countGroupByStatus();
List<Object[]> weekly = orderRepository.findWeeklyRevenue();
```

Weekly revenue uses a native MySQL query (JPQL has no `WEEK()` function equivalent):

```sql
SELECT week_label, revenue FROM (
    SELECT CONCAT(YEAR(created_at), '-W', LPAD(WEEK(created_at, 3), 2, '0')) as week_label,
           YEAR(created_at) as yr,
           WEEK(created_at, 3) as wk,
           SUM(total_amount) as revenue
    FROM orders
    WHERE status IN ('PAID', 'FULFILLED', 'DELIVERED')
    AND created_at >= DATE_SUB(NOW(), INTERVAL 8 WEEK)
    GROUP BY YEAR(created_at), WEEK(created_at, 3),
             CONCAT(YEAR(created_at), '-W', LPAD(WEEK(created_at, 3), 2, '0'))
) sub
ORDER BY yr, wk
```

The subquery structure is required by MySQL 8.4's `only_full_group_by` mode — the CONCAT expression must appear in both SELECT and GROUP BY, which the subquery pattern handles cleanly.

## Consequences
- Memory usage for analytics is O(1) regardless of order volume — the database handles aggregation
- `sumRevenue()` and `avgTotalAmount()` return `null` when no matching rows exist — null checks are required before formatting
- Native query is tightly coupled to MySQL — cannot be trivially switched to another DB without rewriting the weekly revenue query
- `only_full_group_by` enforcement on Aiven MySQL 8.4 differs from local MariaDB — tested and confirmed working on both via the subquery pattern
