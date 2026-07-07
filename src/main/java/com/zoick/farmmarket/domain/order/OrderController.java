package com.zoick.farmmarket.domain.order;
import com.zoick.farmmarket.domain.auth.FarmUserDetails;
import com.zoick.farmmarket.domain.payment.Payment;
import com.zoick.farmmarket.domain.payment.PaymentIntentResult;
import com.zoick.farmmarket.domain.payment.PaymentProvider;
import com.zoick.farmmarket.domain.payment.PaymentRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final PaymentProvider paymentProvider;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;

    //checkout
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CheckoutResponse> checkout(@AuthenticationPrincipal FarmUserDetails principal, @Valid @RequestBody
    CheckoutRequest request) {
        CheckoutInitResult result = orderService.initiateCheckout(principal.getUserId(), request);
        Order order = result.order();
        if (result.isRetry()) {
            String existingSecret = orderService.getClientSecretForOrder(order.getId()).orElseThrow(() -> new IllegalStateException(
                    "Payment record missing clientSecret"));
            return ResponseEntity.ok(CheckoutResponse.retry(order.getId(), existingSecret, order.getStatus().name()));
        }
        try {
            PaymentIntentResult paymentIntent = paymentProvider.createPaymentIntent(order.getTotalAmount(), order.getCurrency(),
                    order.getId().toString());
            try {
                Payment payment = new Payment();
                payment.setOrder(order);
                payment.setProvider("STRIPE");
                payment.setProviderPaymentId(paymentIntent.paymentIntentId());
                payment.setClientSecret(paymentIntent.clientSecret());
                payment.setAmount(order.getTotalAmount());
                payment.setCurrency(order.getCurrency());
                payment.setStatus("PENDING");
                paymentRepository.save(payment);
            } catch (Exception dbEx) {
                //db write failed after stripe succeeded, expire immediately to release stock
                orderService.expireImmediately(order.getId());
                throw new IllegalStateException("Could not record payment. Please try again.");
            }
            LocalDateTime expiresAt= reservationRepository.findAllByOrderId(order.getId()).stream().map(com.zoick.farmmarket.domain.order.Reservation::getExpiresAt)
                    .findFirst().orElse(null);
            return ResponseEntity.ok(CheckoutResponse.of(order.getId(), paymentIntent.clientSecret(), order.getStatus().name(), expiresAt));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            //stripe failed
            log.error("Stripe failed for order {}: {}", order.getId(), e.getMessage(), e);
            orderService.expireImmediately(order.getId());
            throw new IllegalStateException("Payment could not be initiated. Please try again.");
        }
    }

    //customer endpoints
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal FarmUserDetails principal) {
        return ResponseEntity.ok(orderService.getOrdersForUser(principal.getUserId()));
    }
    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> getOrderById(@AuthenticationPrincipal FarmUserDetails principal, @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrderById(orderId, principal.getUserId()));
    }
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> cancelOrder(@AuthenticationPrincipal FarmUserDetails principal, @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, principal.getUserId()));
    }
    @PostMapping("/{orderId}/confirm-delivery")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> confirmDelivery(@AuthenticationPrincipal FarmUserDetails principal, @PathVariable UUID orderId){
        orderService.confirmDelivery(orderId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{orderId}/dispute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> disputeOrder(@AuthenticationPrincipal FarmUserDetails principal, @PathVariable UUID orderId,
                                             @RequestBody DisputeRequest request){
        orderService.disputeOrder(orderId, principal.getUserId(), request.reason());
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{orderId}/client-secret")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getClientSecret(@AuthenticationPrincipal FarmUserDetails principal, @PathVariable
                                                                   UUID orderId){
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if(!order.getUser().getId().equals(principal.getUserId())){
            throw new IllegalArgumentException("Order not found");
        }
        if(order.getStatus() !=OrderStatus.RESERVED){
            throw new IllegalStateException("Order is not RESERVED");
        }
        String secret= orderService.getClientSecretForOrder(orderId).orElseThrow(() -> new IllegalStateException("No payment record found"));
        return ResponseEntity.ok(Map.of("clientSecret", secret));
    }
}
