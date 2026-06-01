package com.zoick.farmmarket.domain.payment;
import java.math.BigDecimal;
public interface PaymentProvider {
    /**
     * Creates a payment intent and returns the clientSecret.
     * clientSecret is passed to the frontend for stripe.js to complete payment
     */
    PaymentIntentResult createPaymentIntent(BigDecimal amount, String currency, String orderId);
    //initiates a refund for a previously completed paymet.
    String refund(String providerPaymentId, BigDecimal amount);
    WebhookEventResult constructWebhookEvent(String payload, String signature);

}
