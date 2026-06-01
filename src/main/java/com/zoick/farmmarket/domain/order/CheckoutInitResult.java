package com.zoick.farmmarket.domain.order;

public record CheckoutInitResult(Order order, boolean isRetry) {}
