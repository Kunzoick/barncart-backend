# ADR-003 — Delivery Slot Booking at RESERVED, Not PAID

## Status
Accepted

## Context
Delivery slots have a fixed capacity. The system must prevent two customers from booking the same slot when it has only one space remaining. The question is: at what point in the checkout flow should the slot be claimed?

The initial implementation booked the slot in the Stripe webhook handler at `PAID`. This created a window: two customers could both start checkout for the same slot, both see it as available, both complete payment, and both receive a booking — exceeding capacity. The slot appeared available right up until payment was confirmed.

## Decision
`deliverySlotRepository.incrementBookedCount()` is called atomically during `initiateCheckout()` at the point the order becomes `RESERVED` — before Stripe is involved at all.

```sql
UPDATE delivery_slot
SET booked_count = booked_count + 1
WHERE id = :slotId
AND booked_count < capacity
```

Returns `0` if the slot is full, causing immediate checkout failure. Returns `1` if the slot was successfully claimed.

The slot must be decremented on every failure path where a `RESERVED` order does not proceed to `PAID`:

| Failure Path | Decrement Trigger |
|---|---|
| Customer cancels | `cancelOrder()` |
| Phase 2 Stripe failure | `expireImmediately()` |
| Reservation timer expires | `ReservationExpiryProcessor.expireOne()` |
| Stripe payment failed webhook | `WebhookService.handlePaymentFailed()` |

The `bookSlot()` call that previously existed in `WebhookService.handlePaymentSucceeded()` was removed.

## Consequences
- Overbooking is eliminated — the slot is claimed before payment, not after
- Four distinct failure paths must each call the slot decrement — any new cancellation path must include this
- Customers who start checkout but do not pay hold a slot for up to 15 minutes (reservation TTL) — this is the intended behavior and acceptable for a farm delivery context
- Slot availability shown to the customer at checkout reflects real committed demand, not just paid orders
