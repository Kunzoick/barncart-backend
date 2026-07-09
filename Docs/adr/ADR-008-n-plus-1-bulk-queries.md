# ADR-008 — N+1 Query Fix: Bulk IN Queries for Order Lists

## Status
Accepted

## Context
`getOrdersForUser()` and `getAllOrdersPaged()` originally loaded a list of orders then fetched items, reservations, and delivery records per order in a loop. For a user with 20 orders this generated 61 database queries per page load (1 for orders + 20 × 3 for associated data). Under any real usage this causes slow responses and connection pool pressure.

## Decision
Order list endpoints use a bulk fetch pattern:

1. Load all orders in a single query
2. Collect all order IDs into a list
3. Run three bulk queries using `IN` clauses — one for items, one for reservations, one for deliveries
4. Assemble results in Java using `Map<UUID, List<T>>` keyed by order ID

```java
List<Order> orders = orderRepository.findAllPagedOrderByCreatedAtDesc(pageable).getContent();
List<UUID> orderIds = orders.stream().map(Order::getId).toList();

List<OrderItem> allItems = orderItemRepository.findAllByOrderIdIn(orderIds);
List<Reservation> allReservations = reservationRepository.findAllByOrderIdIn(orderIds);
List<OrderDelivery> allDeliveries = orderDeliveryRepository.findAllByOrderIdIn(orderIds);

Map<UUID, List<OrderItem>> itemsByOrder = allItems.stream()
    .collect(Collectors.groupingBy(i -> i.getOrder().getId()));
// ... assemble OrderResponse list
```

Total queries: 4 regardless of how many orders are on the page.

All bulk queries use `JOIN FETCH o.order` so that `item.getOrder().getId()` is safe when grouping — no lazy proxy access.

## Consequences
- 4 queries per list request regardless of page size — eliminates N+1 at the data access layer
- Memory usage is bounded by page size × associated records — acceptable for typical order volumes
- `findAllByOrderIdIn` queries must include `JOIN FETCH` for the order association — plain `findAllByOrderIdIn` without JOIN FETCH reintroduces lazy proxy issues when grouping
- If order page size is very large (100+), the `IN` clause itself can become slow — current page size of 20 is well within safe bounds
