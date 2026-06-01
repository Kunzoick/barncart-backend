package com.zoick.farmmarket.domain.payment;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {
    private final PaymentProvider paymentProvider;
    private final WebhookService webhookService;

    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(HttpServletRequest request,  @RequestHeader
            ("Stripe-Signature") String signature){
        //read raw bytes to preserve exact payload for signature verification
        String payload;
        try{
            payload= new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        //verify signature
        WebhookEventResult event;
        try{
            event= paymentProvider.constructWebhookEvent(payload, signature);
        } catch (Exception e) {
            log.error("Failed to construct webhook event: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
        //mark event received in its own committed transaction
        //returns false if already processed -> Stripe retry safe
        if(!webhookService.markEventReceived(event.eventId(), event.paymentIntentId())){
            log.info("Webhook event {} already processed — skipping", event.eventId());
            return ResponseEntity.ok().build();
        }
        //process in separate transaction per event type
        switch (event.eventType()){
            case "payment_intent.succeeded" -> webhookService.handlePaymentSucceeded(event.paymentIntentId(), event.eventId());
            case "payment_intent.payment_failed" -> webhookService.handlePaymentFailed(event.paymentIntentId(), event.eventId());
            default -> log.warn("Unhandled Stripe event type: {}", event.eventType());
        }
        return ResponseEntity.ok().build();
    }
}
