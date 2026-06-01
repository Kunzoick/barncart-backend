CREATE INDEX idx_audit_entity    ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_performer ON audit_log (performed_by);