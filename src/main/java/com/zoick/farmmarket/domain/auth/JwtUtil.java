package com.zoick.farmmarket.domain.auth;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * Handles everything JWT-> generating access tokens, generating refresh tokens, hashing refresh tokens for storage
 * and validating access tokens on incoming requests
 */
@Component
public class JwtUtil {
    private final SecretKey accessKey;
    private final long accessTtlMillis;
    private final long refreshTtlDays;

    public JwtUtil(@Value("${jwt.access-secret}") String accessSecret,
                   @Value("${jwt.access-ttl-minutes}") long accessTtlMillis,
                   @Value("${jwt.refresh-ttl-days}") long refreshTtlDays){
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMillis = accessTtlMillis * 60 * 1000;
        this.refreshTtlDays = refreshTtlDays;
    }
    //Generate access token
    public String generateAccessToken(String email, String role) {
        Date now= new Date();
        Date expiry= new Date(now.getTime() + accessTtlMillis);
        return Jwts.builder().subject(email).claim("role", role)
                .issuedAt(now).expiration(expiry).signWith(accessKey).compact();
    }
    //Extract email from access token
    public String extractEmail(String token){
        return parseClaims(token).getSubject();
    }
    //Extract role from access token
    public String extractRole(String token){
        return parseClaims(token).get("role", String.class);
    }
    //validate access token-> returns false if expired or tampered
    public boolean isAccessTokenValid(String token){
        try{
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    //generate raw refresh token
    public String generateRawRefreshToken(){
        byte[] bytes= new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    //hash raw refresh token for DB storage- SHA-256
    public String hashWithSha256(String rawToken){
        try{
            MessageDigest digest= MessageDigest.getInstance("SHA-256");
            byte[] hash= digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available",e);
        }
    }
    //compute refresh token expiry timestamp
    public LocalDateTime refreshTokenExpiry(){
        return LocalDateTime.now().plusDays(refreshTtlDays);
    }
    private Claims parseClaims(String token){
        return Jwts.parser().verifyWith(accessKey).build().parseSignedClaims(token)
                .getPayload();
    }
}
