package com.zoick.farmmarket.infrastructure.websocket;
import java.util.UUID;
public record OrderStatusChangedEvent(UUID userId, UUID orderId,
                                      String previousStatus, String currentStatus) {
}
