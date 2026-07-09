# ADR-011 — Dispute Flow: DISPUTED Status Freezes Auto-Confirm Scheduler

## Status
Accepted

## Context
Orders move to `FULFILLED` when the admin marks them dispatched. The `DeliveryConfirmationJob` auto-confirms orders to `DELIVERED` 48 hours after fulfillment if the customer has not confirmed manually. Without a dispute mechanism, a customer who did not receive their order has no recourse — the system auto-confirms regardless and the order closes.

## Decision
A `DISPUTED` status was added to the `OrderStatus` enum. The full dispute flow:

1. Customer can dispute any `FULFILLED` order, providing a reason
2. `disputeOrder()` sets status to `DISPUTED` and records `disputedAt` and `disputeReason`
3. `DeliveryConfirmationJob` fetches only `FULFILLED` order IDs — `DISPUTED` orders are excluded at the SQL level
4. Admin sees disputed orders flagged in the order list with an alert indicator
5. Admin resolves the dispute via `resolveDispute()` — status returns to `FULFILLED`
6. The 24hr auto-confirm scheduler resumes naturally from `resolvedAt` time

```sql
-- Only FULFILLED orders are eligible for auto-confirmation
SELECT id FROM orders
WHERE status = 'FULFILLED'
AND updated_at <= :cutoff
```

## Consequences
- Disputed orders are frozen indefinitely until admin action — no customer can be force-confirmed while under investigation
- The `DISPUTED → FULFILLED` transition resets the auto-confirm clock to resolution time, giving customers another 48hr window to manually confirm after the dispute is resolved
- `disputeReason` is a free-text field — no structured dispute categories at this stage
- No customer notification is sent when admin resolves a dispute — this is a known gap; a resolution email would improve UX
- Only `FULFILLED` orders can be disputed — `PAID` orders that were never dispatched use the cancellation flow instead
- The scheduler's underlying query originally had a return-type mismatch (`List<UUID>` declared against a full-entity `SELECT`) that caused it to silently fail every run via `ClassCastException` — see bug log entry BUG-019. The 48-hour window described above was correct in intent from the start; it simply never executed until that bug was fixed
