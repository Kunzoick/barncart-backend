# ADR-007 — Lazy Proxy Fix Pattern: Always JOIN FETCH Before Accessing User

## Status
Accepted

## Context
`OrderResponse.from()` accesses `order.getUser().getEmail()` and `order.getUser().getFirstName()`. The `User` association on `Order` is `@ManyToOne` with default lazy loading. Outside a Hibernate session, accessing a lazy proxy throws `LazyInitializationException`.

This surfaced as a production bug in `confirmDelivery` and `fulfillOrder` — both loaded the order with `findById()`, which returns a proxy for the User association. When `OrderResponse.from()` tried to access user fields, the session was already closed.

## Decision
Any service method that constructs an `OrderResponse` must load the order using `findByIdWithUser()` — a repository method with an explicit `JOIN FETCH`:

```java
@Query("SELECT o FROM Order o JOIN FETCH o.user WHERE o.id = :id")
Optional<Order> findByIdWithUser(@Param("id") UUID id);
```

This eagerly loads the User within the same query, avoiding a second query and eliminating the lazy proxy issue.

For processors that also need order items:

```java
@Query("SELECT o FROM Order o JOIN FETCH o.user JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithOrderAndUser(@Param("id") UUID id);
```

Methods that must use `findByIdWithUser`:
- `confirmDelivery()`
- `fulfillOrder()`
- `resolveDispute()`
- `disputeOrder()`
- `ReservationExpiryProcessor.expireOne()`
- `DeliveryConfirmationProcessor.confirmOne()`

## Consequences
- `LazyInitializationException` is eliminated for all `OrderResponse` construction paths
- One additional JOIN per query — negligible cost given the alternative is a second query or a crash
- Any future method that builds an `OrderResponse` must use `findByIdWithUser` — using plain `findById` followed by `OrderResponse.from()` will crash in production
- This constraint should be documented in `OrderResponse.java` itself as a warning comment for future maintainers
