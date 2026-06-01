package com.zoick.farmmarket.domain.payment;

public record WebhookEventResult(String eventId, String eventType,
                                 String paymentIntentId) {}
