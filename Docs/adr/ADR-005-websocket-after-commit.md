# ADR-005 — WebSocket Events via TransactionalEventListener(AFTER_COMMIT)

## Status
Accepted

## Context
WebSocket broadcasts notify customers of inventory changes and order status updates in real time. If a broadcast is sent during a transaction that later rolls back, the customer receives a state update for a change that never persisted. Their UI shows inventory as reduced or an order as paid when neither is true.

## Decision
WebSocket messages are published as Spring application events inside `@Transactional` methods. The actual WebSocket send is handled by a listener annotated with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.

```java
// Inside a @Transactional service method
eventPublisher.publishEvent(new InventoryUpdateEvent(listingId, newQuantity));

// In the WebSocket service
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleInventoryUpdate(InventoryUpdateEvent event) {
    messagingTemplate.convertAndSend(
        "/topic/listing/" + event.getListingId() + "/inventory",
        new InventoryUpdate(event.getListingId(), event.getQuantityAvailable())
    );
}
```

`AFTER_COMMIT` guarantees the message fires only after the database change is committed and durable. If the transaction rolls back, no event fires and no message is sent.

This pattern is used for:
- `InventoryUpdateEvent` → `/topic/listing/{id}/inventory`
- `OrderStatusChangedEvent` → `/user/queue/orders`

## Consequences
- WebSocket messages are always consistent with DB state — no phantom updates
- If a transaction rolls back for any reason, clients receive no incorrect notification — correct behaviour
- `publishEvent()` must always be called from inside a `@Transactional` context — calling it from a non-transactional method means `AFTER_COMMIT` never fires
- Any future broadcast must follow this pattern — direct `messagingTemplate.convertAndSend()` calls inside transactions are forbidden
