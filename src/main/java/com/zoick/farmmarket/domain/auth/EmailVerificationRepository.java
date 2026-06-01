package com.zoick.farmmarket.domain.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {
    Optional<EmailVerification> findByUserId(UUID userId);
    Optional<EmailVerification> findByCodeHash(String codeHash);
    void deleteByUserId(UUID userId);
}
