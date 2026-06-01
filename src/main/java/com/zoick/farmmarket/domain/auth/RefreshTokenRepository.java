package com.zoick.farmmarket.domain.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    // Revoke all tokens for a user — logout all sessions
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE RefreshToken r
        SET r.revoked = true
        WHERE r.user.id = :userId
        AND r.revoked = false
        """)
    int revokeAllByUserId(@Param("userId") UUID userId);
}
