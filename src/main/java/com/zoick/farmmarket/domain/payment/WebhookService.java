package com.zoick.farmmarket.domain.payment;
import com.zoick.farmmarket.domain.audit.AuditService;
import com.zoick.farmmarket.domain.cart.CartItemRepository;
import com.zoick.farmmarket.domain.cart.CartRepository;
import com.zoick.farmmarket.domain.delivery.DeliverySlotRepository;
import com.zoick.farmmarket.domain.delivery.DeliverySlotService;
import com.zoick.farmmarket.domain.delivery.OrderDeliveryRepository;
import com.zoick.farmmarket.domain.order.*;
import com.zoick.farmmarket.domain.produce.HarvestBatchRepository;
import com.zoick.farmmarket.domain.user.User;
import com.zoick.farmmarket.domain.user.UserRepository;
import com.zoick.farmmarket.infrastructure.websocket.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final HarvestBatchRepository harvestBatchRepository;
    private final DeliverySlotService deliverySlotService;
    private final DeliverySlotRepository deliverySlotRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final UserRepository userRepository;

    //marks event as received in its own committed transaction.
    @Transactional
    public boolean markEventReceived(String eventId, String paymentIntentId){
        if(paymentRepository.findByProviderEventId(eventId).isPresent()){
            return false;
        }
        try {
            //update payment record with eventId
            paymentRepository.findByProviderPaymentId(paymentIntentId).ifPresent(payment -> {
                payment.setProviderEventId(eventId);
                paymentRepository.save(payment);
            });
            return true;
        }catch (DataIntegrityViolationException e){
            //concurrent duplicate event -> treat as already processed, return 200
            log.info("Concurrent webhook event {} detected — treating as duplicate", eventId);
            return false;
        }
    }
    //handles paymentintent succeeded in its own transaction
    @Transactional
    public void handlePaymentSucceeded(String paymentIntentId, String eventId){
        Payment payment= paymentRepository.findByProviderPaymentId(paymentIntentId).orElseThrow(() ->
                new IllegalStateException("No payment found for PaymentIntent: "+ paymentIntentId));
        payment.setStatus("COMPLETED");
        payment.setClientSecret(null);//null out after completion
        paymentRepository.save(payment);

        Order order= payment.getOrder();
        //defense in depth guard
        if(order.getStatus() == OrderStatus.PAID){
            log.info("Order {} already PAID — skipping", order.getId());
            return;
        }
        //force-load user inside this session & brings the user details so the lazy proxy wont affect
        User user= userRepository.findById(order.getUser().getId()).orElseThrow(() ->
                new IllegalStateException("User not found for order: "+ order.getId()));
        String previousStatus= order.getStatus().name();
        //confirm reservation
        reservationRepository.findAllByOrderId(order.getId()).forEach(reservation -> {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservationRepository.save(reservation);
        });
        //increment slot at PAID
        /*orderDeliveryRepository.findByOrderId(order.getId()).ifPresent(delivery -> {
            try{
                deliverySlotService.bookSlot(delivery.getDeliverySlot().getId());
            }catch(IllegalStateException e){
                log.warn("Delivery slot full for order {} — manual intervention required: {}",
                        order.getId(), e.getMessage());
            }
        });
         */
        cartRepository.findByUserId(order.getUser().getId()).ifPresent(cart -> cartItemRepository.deleteAllByCartId(
                cart.getId()));
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        //broadcast order status update to customer
        auditService.log("Order", order.getId(), "ORDER_PAID", order.getUser().getId(),
                Map.of("status", previousStatus),
                Map.of("status", OrderStatus.PAID.name(),
                        "providerPaymentId", paymentIntentId));
        eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getUser().getId(), order.getId(),
                previousStatus, OrderStatus.PAID.name()));
        eventPublisher.publishEvent(new OrderConfirmedEvent(order.getId(), order.getUser().getEmail()));

        log.info("Order {} moved to PAID", order.getId());
    }
    @Transactional
    public void handlePaymentFailed(String paymentIntentId, String eventId){
        Payment payment= paymentRepository.findByProviderPaymentId(paymentIntentId).orElseThrow(() -> new IllegalStateException(
                "No payment found for PaymentIntent: "+ paymentIntentId));
        payment.setStatus("FAILED");
        payment.setClientSecret(null);// null out after failure
        paymentRepository.save(payment);
        Order order= payment.getOrder();
        if(order.getStatus() == OrderStatus.PAYMENT_FAILED){
            log.info("Order {} already PAYMENT_FAILED — skipping", order.getId());
            return;
        }
        String previousStatus= order.getStatus().name();
        reservationRepository.findAllByOrderId(order.getId()).forEach(reservation -> {
            harvestBatchRepository.returnStock(reservation.getBatch().getId(), reservation.getQuantity());
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);
            auditService.log("HarvestBatch", reservation.getBatch().getId(), "STOCK_RETURNED", null,
                    Map.of("reason", "PAYMENT_FAILED"),
                    Map.of("quantityReturned", reservation.getQuantity()));
        });
        //decrement slot atomically since it was booked at RESEREVED
        orderDeliveryRepository.findByOrderId(order.getId()).ifPresent(delivery -> {
            int decremented= deliverySlotRepository.decrementBookedCount(delivery.getDeliverySlot().getId());
            if(decremented == 0){
                log.warn("decrementBookedCount returned 0 for order {} - slot may already be at 0", order.getId());
            }
        });
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);
        //broadcast order status update to customer
        auditService.log("Order", order.getId(), "ORDER_PAYMENT_FAILED", order.getUser().getId(),
                Map.of("status", previousStatus),
                Map.of("status", OrderStatus.PAYMENT_FAILED.name(),
                        "providerPaymentId", paymentIntentId));
        eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getUser().getId(), order.getId(),
                previousStatus, OrderStatus.PAYMENT_FAILED.name()));
        log.info("Order {} moved to PAYMENT_FAILED", order.getId());
    }
}
