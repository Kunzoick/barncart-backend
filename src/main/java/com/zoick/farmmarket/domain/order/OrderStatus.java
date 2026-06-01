package com.zoick.farmmarket.domain.order;

public enum OrderStatus {
    PENDING,          // Order created, reservation being attempted
    RESERVED,         // Stock deducted, reservation active, 15 min timer running
    PAID,             // Stripe webhook confirmed payment success
    FULFILLED,        // Admin confirmed physical delivery complete
    DELIVERED,        // Admin confirmed physical delivery complete
    CANCELLED,        // Explicit cancellation
    EXPIRED,          // Reservation timer ran out — stock returned
    PAYMENT_FAILED,   // Stripe webhook confirmed payment failure — stock returned
    DISPUTED
}
