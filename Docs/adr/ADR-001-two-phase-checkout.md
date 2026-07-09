# ADR-001 — Two-Phase Checkout: Database Operations Separated from Stripe API Call

## Status
Accepted

## Context
The checkout flow requires two distinct operations: creating a reservation and order in the database, then creating a PaymentIntent via the Stripe API. The naive implementation wraps both in a single `@Transactional` method — but this holds a database connection open for the entire duration of the Stripe HTTP call, which typically takes 200–800ms under normal conditions and can stall indefinitely if Stripe is degraded.

On a free-tier deployment with a HikariCP pool of 5 connections, even moderate traffic can exhaust the pool. Five concurrent checkouts each waiting on Stripe means zero available connections for any other request in the system.

## Decision
`OrderService.initiateCheckout()` is split into two explicit phases.

**Phase 1** runs inside `@Transactional`:
- Validate cart contents and stock availability
- Deduct stock atomically
- Book delivery slot
- Create `Order` and `Reservation` records
- Return the order ID

**Phase 2** runs outside any transaction in the controller:
- Call `stripePaymentProvider.createPaymentIntent()`
- Save the returned `clientSecret` to the `Payment` record
- Return `clientSecret` and `reservationExpiresAt` to the client

If Phase 1 succeeds but Phase 2 fails, `expireImmediately()` is called as a compensating transaction — returning stock, decrementing the slot, and marking the reservation EXPIRED.

If Phase 2 succeeds but saving the `clientSecret` to the DB fails, the same compensation runs. The Stripe PaymentIntent is abandoned — the customer is told to retry.

```java
// Controller — explicit two-phase separation
CheckoutInitResult result = orderService.initiateCheckout(user.getUserId(), request);
try {
    PaymentIntentResult pi = paymentProvider.createPaymentIntent(...);
    orderService.saveClientSecret(result.getOrderId(), pi.getClientSecret());
    return ResponseEntity.ok(new CheckoutResponse(...));
} catch (Exception e) {
    orderService.expireImmediately(result.getOrderId());
    throw e;
}
```

## Consequences
- DB connection is released before Stripe is called — no connection pool exhaustion under slow Stripe responses
- Failure handling is explicit and visible in the controller rather than hidden inside a service
- Two failure modes exist and are handled independently: DB failure (Phase 1) and Stripe failure (Phase 2)
- `expireImmediately()` must correctly undo all Phase 1 side effects: stock return, slot decrement, reservation expiry — any future changes to Phase 1 must update the compensating path
- The Stripe PaymentIntent may be created but never used if Phase 2's DB save fails — these abandoned intents appear in the Stripe dashboard but have no customer impact
