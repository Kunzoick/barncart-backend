package com.zoick.farmmarket.domain.payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Two critical lookups by providerPaymentId to find an order when a stripe webhook arrives and
 * by providerEventId to check if we have already processed that webhook event
 */

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);//webhook lookup(find payment by stripe paymentIntentId)
    Optional<Payment> findByProviderEventId(String providerEventId);//webhook idempotency guard-> check if event already processed
    List<Payment> findAllByOrderId(UUID orderId);//all payment attempts for an order
    Optional<Payment> findByOrderIdAndStatus(UUID orderId, String Status);//most common query
}