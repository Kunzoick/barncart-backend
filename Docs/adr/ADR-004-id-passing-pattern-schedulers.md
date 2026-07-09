# ADR-004 — Reservation Expiry via Scheduler with ID-Passing Pattern

## Status
Accepted

## Context
Scheduled jobs process multiple entities in a batch. The natural approach — load entities in the job, iterate, process each — causes `LazyInitializationException`. Hibernate sessions close after the job's query returns. Any lazy association accessed later in a processor throws because the original session is gone.

Additionally, wrapping the entire batch in a single transaction means one failure rolls back all successfully processed items in that run.

## Decision
All scheduled jobs follow the ID-passing pattern:

1. The **Job** class loads only IDs — a lightweight query with no entity associations
2. The **Processor** class receives one ID, reloads everything it needs inside its own `@Transactional` method, processes, and commits

```java
// ReservationExpiryJob — loads IDs only
@Scheduled(fixedDelay = 120_000)
public void run() {
    List<UUID> expiredIds = reservationRepository.findExpiredActiveReservationIds(LocalDateTime.now());
    for (UUID id : expiredIds) {
        processor.expireOne(id);
    }
}

// ReservationExpiryProcessor — fresh transaction per ID
@Transactional
public void expireOne(UUID reservationId) {
    Reservation reservation = reservationRepository.findByIdWithOrder(reservationId)
        .orElseThrow(...);
    // process with live session and fresh entities
}
```

Each `expireOne()` call is its own transaction. A failure on one reservation logs the error and continues — other reservations in the same run are unaffected.

This pattern is applied consistently to:
- `ReservationExpiryJob` / `ReservationExpiryProcessor`
- `DeliveryConfirmationJob` / `DeliveryConfirmationProcessor`

## Consequences
- `LazyInitializationException` is eliminated — entities are always loaded inside a live transaction
- One failure does not affect other entities in the same scheduler run
- Jobs are lightweight — only ID queries, no entity graphs
- Any future scheduled job that processes entities must follow this pattern — loading entities in the job itself is forbidden
- Processors must use `JOIN FETCH` queries where associations are needed, not rely on lazy loading
