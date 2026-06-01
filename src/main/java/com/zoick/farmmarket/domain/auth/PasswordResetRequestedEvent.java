package com.zoick.farmmarket.domain.auth;

public record PasswordResetRequestedEvent(String email, String resetLink) {
}
