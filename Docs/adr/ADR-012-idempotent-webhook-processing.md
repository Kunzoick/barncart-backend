# ADR-012 — Idempotent Webhook Processing

## Status
Accepted

## Context
Stripe retries webhook delivery on non-200 responses and occasionally sends duplicate events. Without idempotency, a `payment_intent.succeeded` event processed twice would mark an order `PAID` twice — potentially sending duplicate confirmation emails, double-clearing the cart, and triggering double audit log entries.

## Decision
`WebhookService.markEventReceived()` stamps the Stripe event ID onto the `Payment` record before any processing begins. The `provider_event_id` column has a `UNIQUE` constraint. A second call with the same event ID is rejected by the constraint and returns `false`, skipping all processing.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean markEventReceived(String eventId, String paymentIntentId) {
    Optional<Payment> existing = paymentRepository.findByProviderPaymentId(paymentIntentId);
    if (existing.isEmpty()) return false;
    if (existing.get().getProviderEventId() != null) return false;  // already stamped

    try {
        existing.get().setProviderEventId(eventId);
        paymentRepository.save(existing.get());
        return true;
    } catch (DataIntegrityViolationException e) {
        // Two concurrent duplicates — one wins the constraint, one catches here
        return false;
    }
}
```

`REQUIRES_NEW` ensures the stamp is committed before `handlePaymentSucceeded()` begins — if processing fails, the stamp remains in the DB. Stripe will retry, but the stamp is already there and the retry returns `false` immediately.

## Consequences
- Duplicate events are handled correctly even under concurrent delivery
- `DataIntegrityViolationException` catch handles the race condition where two identical events arrive simultaneously and both pass the initial `isPresent()` check
- **Known tradeoff:** If processing fails after the stamp is committed, Stripe retries are silently ignored — the retry sees the stamp and skips. This means processing errors are not automatically retried by Stripe. Manual intervention or a separate retry mechanism would be required for processing failures
- `markEventReceived` runs in its own committed transaction (`REQUIRES_NEW`) — it is not affected by the caller's transaction rollback
