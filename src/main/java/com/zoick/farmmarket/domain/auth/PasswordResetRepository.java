package com.zoick.farmmarket.domain.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {
    Optional<PasswordReset> findByUserId(UUID userId);
    Optional<PasswordReset> findByTokenHash(String tokenHash);
    void deleteByUserId(UUID userId);
}
