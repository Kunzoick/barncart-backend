package com.zoick.farmmarket.domain.auth;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_verification")
@Access(AccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
// Short-lived record — created on register, deleted after successful verification.
// One record per user enforced by UNIQUE on user_id
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;
    @Column(nullable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID userId;
    @Column(nullable = false, length = 64)
    private String codeHash;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
