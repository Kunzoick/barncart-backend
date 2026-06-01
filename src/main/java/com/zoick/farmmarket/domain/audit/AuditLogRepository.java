package com.zoick.farmmarket.domain.audit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

/**
 * Append only-> no updates or deletes ever. Queries are by entity for audit trail display
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findAllByEntityTypeAndEntityId(String entityType, UUID entityId);
    List<AuditLog> findAllByPerformedById(UUID performedById);
}