package com.zoick.farmmarket.domain.auth;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String email, String rawCode) {
}
