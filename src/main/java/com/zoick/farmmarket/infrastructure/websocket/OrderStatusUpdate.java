package com.zoick.farmmarket.infrastructure.websocket;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
//payload sent when order status transitions. Private channel-> only the order owner receives it
public class OrderStatusUpdate {
    private UUID orderId;
    private String previousStatus;
    private String currentStatus;
    private LocalDateTime timestamp;
}
