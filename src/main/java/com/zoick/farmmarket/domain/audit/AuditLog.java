package com.zoick.farmmarket.domain.audit;

import com.zoick.farmmarket.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Access(AccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
//Append-only — no updatedAt, no @PreUpdate.
// performedBy is nullable because system-triggered events like expiry jobs have no user
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;
    @Column(nullable = false, length = 50)
    private String entityType;
    @Column(nullable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID entityId;
    @Column(nullable = false, length = 100)
    private String action;
    @Column(name = "performed_by", columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID performedById;
    @Column(columnDefinition = "JSON")
    private String oldValue;
    @Column(columnDefinition = "JSON")
    private String newValue;
    @Column(columnDefinition = "JSON")
    private String metadata;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}