package com.zoick.farmmarket.domain.payment;

public record PaymentIntentResult(
   String paymentIntentId,
   String clientSecret
) {}
