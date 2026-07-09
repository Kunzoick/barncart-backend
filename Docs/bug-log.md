# BarnCart Bug Log

Bugs encountered and resolved during development and deployment. Ordered chronologically.

---

## BUG-001 — LazyInitializationException in confirmDelivery and fulfillOrder

**Symptom:** `LazyInitializationException` thrown when building `OrderResponse` after `confirmDelivery()` and `fulfillOrder()` calls.

**Root cause:** Both methods loaded orders with `findById()`, which returns a Hibernate proxy for the `User` association. `OrderResponse.from()` calls `order.getUser().getEmail()` — the proxy was accessed outside an active session.

**Fix:** Replaced `findById()` with `findByIdWithUser()` — a repository method using `JOIN FETCH o.user`. Applied the same pattern to all methods that construct `OrderResponse`.

**Pattern established:** See ADR-007.

---

## BUG-002 — ReservationExpiryProcessor LazyInitializationException

**Symptom:** Reservation expiry job crashed with `LazyInitializationException` when accessing order associations in the processor.

**Root cause:** Job loaded full `Reservation` entities including associations. Hibernate session closed after the job's query. Processor accessed lazy associations outside a live session.

**Fix:** Refactored to ID-passing pattern — job loads only reservation IDs, processor reloads everything inside its own `@Transactional`.

**Pattern established:** See ADR-004.

---

## BUG-003 — Slot overbooking under concurrent checkout

**Symptom:** Two users could both complete checkout for the same slot when one space remained, resulting in `booked_count > capacity`.

**Root cause:** Slot booking was done at `PAID` in the webhook handler. Both users passed the availability check before either payment was confirmed.

**Fix:** Moved slot booking to `RESERVED` — the slot is claimed atomically at checkout initiation, before Stripe is involved. Added slot decrement to all failure paths.

**Pattern established:** See ADR-003.

---

## BUG-004 — Duplicate webhook processing

**Symptom:** Occasional duplicate order confirmation emails and double audit log entries.

**Root cause:** Stripe retried webhook delivery on slow responses. The webhook handler had no idempotency check.

**Fix:** Added `provider_event_id` with a `UNIQUE` constraint to the `Payment` table. `markEventReceived()` stamps the event ID before processing. Added `DataIntegrityViolationException` catch for concurrent duplicate delivery.

**Pattern established:** See ADR-012.

---

## BUG-005 — DisabledException not handled on login

**Symptom:** Inactive users (unverified accounts) received a `500` internal server error instead of a meaningful message when attempting to login.

**Root cause:** Spring Security throws `DisabledException` for inactive users. `GlobalExceptionHandler` had no handler for this exception type.

**Fix:** Added `@ExceptionHandler(DisabledException.class)` returning `403` with a clear message: "Account not yet verified."

---

## BUG-006 — bookSlot called twice at payment success

**Symptom:** `booked_count` incremented twice for orders that reached `PAID` — once at `RESERVED` (correct) and once in the webhook handler (leftover code).

**Root cause:** After moving slot booking to `RESERVED`, the `bookSlot()` call in `WebhookService.handlePaymentSucceeded()` was not removed.

**Fix:** Removed `bookSlot()` from `handlePaymentSucceeded()`. Confirmed slot decrement still runs on `handlePaymentFailed()`.

---

## BUG-007 — Stock double-deduction on cart resubmit

**Symptom:** Stock could be deducted multiple times if a user resubmitted checkout without clearing their cart.

**Root cause:** `CartService` did not validate whether items already had active reservations before deducting stock.

**Fix:** Added batch status check and active reservation check in `CartService` before proceeding to stock deduction.

---

## BUG-008 — findPendingConfirmationBefore missing JOIN FETCH

**Symptom:** `LazyInitializationException` in `DeliveryConfirmationProcessor` when accessing `reservation.getOrder()`.

**Root cause:** `findPendingDeliveryIdsBefore` query did not JOIN FETCH the order association. Processor accessed it outside the query's session.

**Fix:** Added `JOIN FETCH` on the order in the query.

---

## BUG-009 — Flyway V13 failed on fresh Aiven database

**Symptom:** Backend startup failed on Render with FK constraint violation in `V13__seed_produce_and_categories.sql`.

**Root cause:** The `Vegetables` category (UUID `d7875f3d-...`) and `Tomatoes` produce were manually inserted into the local MariaDB database and never added to any migration. V13 referenced both by hardcoded UUID. Fresh Aiven database had neither.

**Fix:** Added `V16__fix_missing_vegetables_and_tomatoes.sql` with `INSERT IGNORE` for both records. Manually deleted the failed V13 entry from `flyway_schema_history` on Aiven using a Node.js script.

---

## BUG-010 — Analytics 500 on Aiven MySQL 8.4

**Symptom:** `GET /api/admin/analytics` returned 500 on production. Worked locally on MariaDB.

**Root cause:** Aiven MySQL 8.4 enforces `only_full_group_by` mode. The weekly revenue query used `CONCAT(YEAR(...), ...)` in SELECT but not in GROUP BY — rejected by MySQL 8.4, silently accepted by MariaDB.

**Fix:** Rewrote the weekly revenue query using a subquery. The inner query groups by `YEAR`, `WEEK`, and the full `CONCAT` expression. The outer query selects and orders from the subquery — satisfying `only_full_group_by` on both engines.

---

## BUG-011 — V17 delivery slot INSERT using wrong column names

**Symptom:** V17 migration failed with `Unknown column 'date' in 'field list'`.

**Root cause:** Delivery slot INSERT used column names `date`, `time_of_day`, and `bookings_count`. The actual schema (V1) uses `slot_date`, `slot_type`, and `booked_count`.

**Fix:** Corrected all three column names in V17. Deleted the failed V17 entry from `flyway_schema_history` on Aiven before redeploying.

---

## BUG-012 — ProduceUnit validation failure on produce creation

**Symptom:** `POST /api/produce` returned 400 with `HV000030: No validator could be found for constraint 'NotBlank' validating type 'ProduceUnit'`.

**Root cause:** `CreateProduceRequest` used `@NotBlank` on the `unit` field. `@NotBlank` only works on `String` — `ProduceUnit` is an enum, which `@NotBlank` cannot validate.

**Fix:** Changed `@NotBlank` to `@NotNull` on the `unit` field in `CreateProduceRequest`.

---

## BUG-013 — Mobile checkout double submission

**Symptom:** On mobile, the "Reserve & Pay" button sometimes triggered two checkout requests, causing a "resource already exists" error on the second attempt.

**Root cause:** Mobile browsers can fire multiple touch events or re-render the component between taps, triggering `handleCheckout` twice before the loading state disabled the button.

**Fix:** Added `reservationMade` state flag. `handleCheckout` returns immediately if `reservationMade` is true. Added recovery logic — if the "already exists" error is caught, fetch the existing RESERVED order and resume at step 3 instead of showing an error.

---

## BUG-014 — Silent multi-item order data loss via clearAutomatically

**Symptom:** Orders with more than one distinct item only ever showed 1 item in order history and admin views, despite Render logs showing a "Saved order item for: X" line for every item in the cart. No exception was thrown.

**Root cause:** `initiateCheckout()`'s loop calls `harvestBatchRepository.deductStock()` — a `@Modifying(clearAutomatically = true)` query — once per cart item, interleaved with `reservationRepository.save()` and `orderItemRepository.save()` calls. `save()` on a new entity only schedules an insert (`persist()`); it doesn't flush to the DB. `clearAutomatically = true` detaches the entire persistence context after each `deductStock()` call, silently discarding any prior iteration's unflushed `Reservation`/`OrderItem`. Only the last iteration's entities survived, since no subsequent `deductStock()` call cleared them before the transaction's final commit.

Confirmed via direct query: `SELECT COUNT(*) FROM order_item WHERE order_id = '...'` returned `1` for an order with 4 logged item saves.

**Fix:** Changed `orderItemRepository.save(orderItem)` to `orderItemRepository.saveAndFlush(orderItem)` in the checkout loop — forces the pending `Reservation` and `OrderItem` to flush to the DB before the next iteration's `clearAutomatically` can wipe them.

**Fallout — 7 broken call sites:** Fixing the write path exposed that `ReservationRepository.findByOrderId()` (`Optional<Reservation>`) was called in 7 places, all assuming exactly one reservation per order — an assumption that was only ever true because of this bug. Once multi-item orders correctly persisted all reservations, these call sites either crashed (`NonUniqueResultException`) or silently processed only one of several reservations. Added `findAllByOrderId()` and migrated all 7 call sites (`OrderService.expireImmediately`, `cancelOrder`, `getOrderById`, `getAllOrders`, `OrderController.checkout`, `WebhookService.handlePaymentSucceeded`, `handlePaymentFailed`) to iterate the full list rather than assume a single result.

**Pattern established:** See ADR-015.

**Known gap:** Orders placed before this fix may still have incomplete data in `order_item` and `reservation` tables. No historical data repair was performed.

---

## BUG-018 — Expired access token causing silent 403 instead of 401, breaking silent refresh

**Symptom:** Authenticated admin actions (e.g. `POST /api/produce`) intermittently failed with 403 Forbidden roughly every 15 minutes of active use, requiring a manual logout/login to recover. No console error beyond the raw 403.

**Root cause:** The axios response interceptor in `axios.js` only attempted a silent token refresh on `401` responses. An expired-but-present JWT reaching a `@PreAuthorize("hasRole('ADMIN')")`-guarded endpoint was rejected by Spring Security as `403 Forbidden`, not `401 Unauthorized` — Spring Security's default behavior distinguishes "not authenticated" (401) from "authenticated but denied" (403), and an expired token that still parses (just fails validity checks) can be routed to the 403 path depending on where in the filter chain it's rejected. Since the interceptor never matched on 403, no refresh was attempted, and the original request failed outright.

**Fix:** Extended the interceptor's retry condition to include 403:
```javascript
if ((error.response?.status === 401 || error.response?.status === 403) && !original._retry && !isAuthEndpoint) { ... }
```
Confirmed via Render logs and browser network tab that subsequent requests after token expiry now trigger `POST /api/auth/refresh` and succeed on retry.

**Known gap:** Broadening retry-on-403 is a blunt instrument — it will also attempt a refresh+retry for legitimate 403s (e.g. a non-admin user hitting an admin endpoint), adding one extra round-trip before the real 403 is surfaced to the user. A more precise fix would inspect the response body for a specific "token expired" error code before deciding to refresh, rather than keying off status code alone.

---

## BUG-019 — DeliveryConfirmationJob query return-type mismatch

**Symptom:** The 48-hour auto-confirm scheduler never actually confirmed any deliveries. No customer-visible symptom until orders were checked days later and found stuck in `FULFILLED` well past the 48-hour window.

**Root cause:** `OrderDeliveryRepository.findPendingConfirmationBefore()` was declared with return type `List<UUID>`, but its JPQL selected the full entity (`SELECT od FROM OrderDelivery od ...`). Spring Data JPA attempted to cast each returned `OrderDelivery` into the declared `UUID` type, throwing a `ClassCastException` on every scheduler run.

**Fix:** Changed the query to explicitly select the ID field:
```java
@Query("""
SELECT od.id FROM OrderDelivery od
JOIN od.order o
WHERE od.fulfilledAt IS NOT NULL
AND od.fulfilledAt < :cutoff
AND od.customerConfirmedAt IS NULL
AND od.autoConfirmed = false
AND o.status = 'FULFILLED'
""")
List<UUID> findPendingConfirmationBefore(@Param("cutoff") LocalDateTime cutoff);
```

**Note:** This bug predates and is unrelated to the 24hr/48hr window value itself — see ADR-011 correction; the window was always intended to be 48 hours in code, only the ADR text lagged.

---

## BUG-021 — Mobile session dropped on refresh due to missing SameSite=None on refresh cookie

**Symptom:** On mobile browsers, refreshing the page (or the app losing/regaining foreground) logged the user out, even though the httpOnly refresh cookie was present and valid. Desktop was unaffected.

**Root cause:** `AuthController`'s cookie-setting code originally used the servlet `Cookie` class, which has no `setSameSite()` method. The refresh cookie was emitted without any explicit `SameSite` attribute. A cookie with no `SameSite` attribute defaults to `SameSite=Lax`, which blocks the cookie on cross-site subrequests in some mobile browser contexts (frontend on Vercel, backend on Render — different origins). Desktop Chrome's slightly different cross-origin cookie handling masked the issue during initial testing.

**Fix:** Replaced the `jakarta.servlet.http.Cookie`-based cookie construction with a manually built `Set-Cookie` header string, allowing explicit control over `SameSite`:
```java
String cookie = REFRESH_COOKIE + "=" + rawRefreshToken
        + "; Max-Age=" + SEVEN_DAYS
        + "; Path=/api/auth"
        + "; HttpOnly"
        + (cookieSecure ? "; Secure; SameSite=None" : "; SameSite=Lax");
response.addHeader("Set-Cookie", cookie);
```
`SameSite=None` (which requires `Secure`) is used in production; `SameSite=Lax` remains the local-dev fallback per the existing `APP_COOKIE_SECURE` flag documented in ADR-006. Applied identically to `clearRefreshCookie()`.

**Pattern established:** Cross-referenced in ADR-006 — that ADR now reflects the manual header construction actually used, not the `Cookie` object that was silently insufficient.
