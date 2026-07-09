# ADR-015 — Explicit Flush in Checkout Loop and List-Based Reservation Handling

## Status
Accepted

## Context
`initiateCheckout()` loops over cart item snapshots, calling `harvestBatchRepository.deductStock()` (an `@Modifying(clearAutomatically = true)` query) once per snapshot, then persisting a `Reservation` and an `OrderItem` for that snapshot via `save()`.

`save()` on a new entity calls `persist()`, which schedules an insert but does not execute it immediately — the insert sits in the Hibernate persistence context until a flush occurs. `clearAutomatically = true` on `deductStock()` detaches the entire persistence context after the query runs, including any entities that were `persist()`-ed but not yet flushed.

For a multi-item order, this meant: iteration 1's `Reservation`/`OrderItem` were persisted but unflushed; iteration 2's `deductStock()` call cleared them silently before they ever reached the database. This repeated on every iteration. Only the final iteration's entities survived, since no subsequent `deductStock()` call existed to clear them before the transaction's final commit-time flush.

The result: every multi-item order silently lost all but the last item's `Reservation` and `OrderItem` row. Stock was correctly deducted for all items (the raw `UPDATE` always executed), but reservation tracking and order history were incomplete for every such order. No exception was thrown — the bug was entirely silent, discovered only by directly querying `order_item` row counts against expected cart size.

Fixing this write-path bug then exposed a second, pre-existing bug: every consumer of `ReservationRepository.findByOrderId()` (`Optional<Reservation>`) assumed exactly one reservation per order. This was never true for multi-item orders — it only appeared true because the flush bug silently discarded all but one reservation. Once multiple reservations correctly persisted, these call sites began throwing `NonUniqueResultException` or, in the case of the payment webhook handlers, silently processing only one reservation while leaving the others in an inconsistent state (e.g., stock never returned for the un-processed batches on payment failure).

## Decision
**Write-path fix:** `orderItemRepository.save(orderItem)` was changed to `orderItemRepository.saveAndFlush(orderItem)` inside the checkout loop. Flushing at the end of each iteration pushes the entire pending persistence context — including the `Reservation` saved earlier in the same iteration — to the database before the next iteration's `deductStock()` call can clear it.

**Read-path fix:** A new repository method `List<Reservation> findAllByOrderId(UUID orderId)` was added. All 7 call sites previously using `findByOrderId()` (`Optional<Reservation>`) were migrated to `findAllByOrderId()` and updated to iterate:

- `OrderService.expireImmediately()` — iterates all reservations, returns stock and expires each
- `OrderService.cancelOrder()` — iterates all reservations, returns stock and cancels each, publishes one inventory event per batch
- `OrderService.getOrderById()` / `getAllOrders()` / `OrderController.checkout()` — take `.findFirst()` on `expiresAt` since all reservations on one order share the same expiry (set in the same loop iteration)
- `WebhookService.handlePaymentSucceeded()` — confirms all reservations, not just one
- `WebhookService.handlePaymentFailed()` — returns stock and cancels all reservations, not just one

The old `findByOrderId()` method was left in place rather than removed, since `OrderDelivery` — a genuinely 1:1 relationship with `Order` — uses an analogous single-result pattern that remains correct and was not part of this fix.

## Consequences
- New orders correctly persist one `Reservation` and one `OrderItem` per distinct cart line, regardless of item count
- `saveAndFlush` adds one flush per loop iteration — for typical cart sizes (2–10 items) this is negligible overhead, but it is N flushes instead of 1
- Historical orders placed before this fix may still have incomplete `order_item`/`reservation` data — this fix does not repair existing rows. A data audit comparing `order_item` counts against expected cart size (or cross-referencing `audit_log` `STOCK_DEDUCTED` entries) would be required to quantify and repair historical damage, and was not performed as part of this fix
- Any future code that assumes "one reservation per order" is now incorrect by design — `Reservation` is 1:N with `Order` (one per distinct batch), not 1:1. This should be treated as a standing constraint for any new feature touching reservations
- The interleaved pattern (persist → `@Modifying` clear → persist → clear) that caused the original bug still exists structurally in `initiateCheckout()`. The fix patches the symptom via explicit flush rather than restructuring the loop to separate all stock deductions from all entity persistence into two passes. A future refactor removing the interleaving entirely would eliminate this class of bug at the structural level rather than relying on flush-timing correctness
