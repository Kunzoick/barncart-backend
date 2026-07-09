# ADR-013 — Audit Log: No Foreign Key on performed_by

## Status
Accepted

## Context
`AuditLog.performedById` records which user triggered an auditable action. The initial schema added a foreign key from `audit_log.performed_by` to `users.id`. This created two problems.

First, system-triggered events — reservation expiry jobs, auto-delivery confirmation — have no associated user. They require either a nullable FK (which MySQL allows but is architecturally awkward) or a dedicated "system" user record that must exist in the database.

Second, the FK creates a hidden coupling: if a user record is ever deleted or anonymised, the FK blocks deletion while audit entries referencing that user exist. The audit log holds user records hostage.

## Decision
`V15__remove_audit_fk.sql` explicitly drops the FK constraint:

```sql
ALTER TABLE audit_log DROP FOREIGN KEY audit_log_ibfk_1;
```

`performed_by` is stored as a plain `CHAR(36)` UUID column with no JPA association and no database-level constraint. `AuditLog` in Java stores `UUID performedById` — no `@ManyToOne User` reference.

System events write `null` for `performedById`. User actions write the authenticated user's ID.

`AuditService` has no `@Transactional` annotation — it joins the caller's transaction. If the caller rolls back, the audit entry rolls back with it, keeping audit history consistent with actual committed state.

## Consequences
- System events can be audited without requiring a fake user record
- User deletion is not blocked by audit history — users can be deleted or anonymised without FK violations
- Referential integrity for `performed_by` is enforced at the application layer only — a bug could write a non-existent user ID with no DB-level rejection
- `AuditLog` cannot JOIN to `User` in native queries — if audit reporting needs user details, the application must look them up separately by the stored UUID
- Any query that reads `performed_by` must handle `null` — system events always have a null performer
