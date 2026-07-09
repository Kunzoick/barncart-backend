# ADR-002 — Atomic Stock Deduction with Optimistic Concurrency

## Status
Accepted

## Context
Inventory deduction requires checking available quantity and then reducing it. The naive approach loads the entity, checks the field in Java, and saves. This read-then-write pattern has a race condition: two concurrent requests can both read `quantity_available = 5`, both pass the check, and both deduct — resulting in `-5` available stock being written to the database.

Pessimistic locking (`SELECT FOR UPDATE`) would solve this but introduces lock contention and can cause deadlocks under concurrent access. Adding an `@Version` field for optimistic locking requires retry logic at the application layer.

## Decision
Stock deduction uses a single atomic `UPDATE` query with an inline guard:

```sql
UPDATE harvest_batch
SET quantity_available = quantity_available - :quantity
WHERE id = :batchId
AND quantity_available >= :quantity
```

The return value (number of rows affected) is checked immediately. If it returns `0`, stock is insufficient — an exception is thrown and the transaction rolls back. If it returns `1`, the deduction succeeded atomically.

```java
int updated = harvestBatchRepository.deductStock(batchId, quantity);
if (updated == 0) {
    throw new IllegalStateException("Insufficient stock for batch: " + batchId);
}
```

The same pattern is used for the inverse operation — returning stock:

```sql
UPDATE harvest_batch
SET quantity_available = LEAST(quantity_available + :quantity, quantity_original)
WHERE id = :batchId
```

`LEAST()` guards against returning more stock than was originally harvested, which could happen if a bug causes a double-return.

## Consequences
- Race conditions on stock deduction are eliminated at the database level
- No pessimistic locking, no `@Version` field, no retry logic required
- The Hibernate entity cache is stale after this `@Modifying` query — `clearAutomatically = true` is set on the annotation to force a fresh load on next access
- Any code that reads `harvestBatch.getQuantityAvailable()` after a deduction must reload the entity or operate on the return value, not the cached entity field
- The upper-bound guard on stock return (`LEAST`) must be maintained if the return logic changes in future
- `@Modifying(clearAutomatically = true)` detaches the **entire** persistence context, not just the target entity. This has caused two distinct production bugs from the same root cause: (1) stale references to previously-fetched entities accessed later in the same transaction — fixed via a snapshot pattern that extracts primitive data before any `@Modifying` call runs; (2) silent loss of `persist()`-ed-but-unflushed entities when a subsequent `@Modifying` call clears the context before a flush occurs — see ADR-015. Any code that interleaves entity persistence with `@Modifying` calls inside the same transaction must either flush explicitly after each persist, or restructure to separate all `@Modifying` calls from all persistence into two passes
