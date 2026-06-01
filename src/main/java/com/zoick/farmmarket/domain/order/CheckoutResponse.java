package com.zoick.farmmarket.domain.order;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CheckoutResponse {
    private UUID orderId;
    private String clientSecret;
    private String status;
    private boolean isRetry;
    private LocalDateTime reservationExpiresAt;

    public static CheckoutResponse of(UUID orderId, String clientSecret, String status, LocalDateTime reservationExpiresAt){
        return new CheckoutResponse(orderId, clientSecret, status, false, reservationExpiresAt);
    }
    public static CheckoutResponse retry(UUID orderId, String clientSecret, String status){
        return new CheckoutResponse(orderId, clientSecret, status, true, null);
    }
}
