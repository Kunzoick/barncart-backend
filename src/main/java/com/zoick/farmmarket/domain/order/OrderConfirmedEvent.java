package com.zoick.farmmarket.domain.order;

import java.util.UUID;
//carries only the IDs
public record OrderConfirmedEvent(UUID orderId, String userEmail) {}
