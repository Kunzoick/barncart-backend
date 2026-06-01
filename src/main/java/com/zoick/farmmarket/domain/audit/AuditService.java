package com.zoick.farmmarket.domain.audit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
// Thin append-only audit writer. No @Transactional — joins the caller's transaction.
// If the caller rolls back, the audit entry rolls back with it.
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(String entityType, UUID entityId, String action, UUID performedBy,
                    Object oldValue, Object newValue){
        AuditLog entry = new AuditLog();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setPerformedById(performedBy);
        entry.setOldValue(toJson(entityType, action, oldValue));
        entry.setNewValue(toJson(entityType, action, newValue));
        auditLogRepository.save(entry);
    }
    private String toJson(String entityType, String action, Object value){
        if(value== null) return null;
        try{
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Audit serialization failed for entityType={} action={} valueType={}: {}",
                    entityType, action, value.getClass().getSimpleName(), e.getMessage());
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
