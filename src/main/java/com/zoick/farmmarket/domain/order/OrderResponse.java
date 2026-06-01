package com.zoick.farmmarket.domain.order;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class OrderResponse {
    private UUID orderId;
    private UUID userId;
    private String customerEmail;
    private String customerFirstName;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private String pricingType;
    private String refundStatus;
    private String cancellationReason;
    private String disputeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime reservationExpiresAt;
    private List<OrderItemResponse> items;

    public static OrderResponse from(Order order, List<OrderItemResponse> items, LocalDateTime reservationExpiresAt, String disputeReason){
        return OrderResponse.builder().orderId(order.getId()).userId(order.getUser().getId()).customerEmail(order.getUser().getEmail())
                .customerFirstName(order.getUser().getFirstName()).status(order.getStatus().name())
                .totalAmount(order.getTotalAmount()).currency(order.getCurrency()).pricingType(order.getPricingType().name())
                .refundStatus(order.getRefundStatus().name()).cancellationReason(order.getCancellationReason()).disputeReason(disputeReason)
                .createdAt(order.getCreatedAt()).updatedAt(order.getUpdatedAt()).reservationExpiresAt(reservationExpiresAt).items(items).build();
    }
/*
order.getUser().getEmail() and order.getUser().getFirstName() are called directly in from() which is a static method with no transaction context.
This is fine only because every caller in OrderService is inside a @Transactional method where the session is still open when from() is called.
 Don't move from() calls outside transactions in the future.
 */
}
