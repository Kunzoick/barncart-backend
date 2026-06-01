package com.zoick.farmmarket.domain.payment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

//replaces the stub. calls stripe API to create paymentIntents, process refunds and verify webhook signatures
@Service
public class StripePaymentProvider implements PaymentProvider{
    @Value("${stripe.webhook-secret}")
    private String webhookSecret;
    private final ObjectMapper objectMapper;
    public StripePaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    @Override
    public PaymentIntentResult createPaymentIntent(BigDecimal amount, String currency, String orderId){
        try{ //round before converting to avoid truncation
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).setScale(0,
                    RoundingMode.HALF_UP).longValueExact();
            PaymentIntentCreateParams params =  PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents).setCurrency(currency.toLowerCase())
                    .putMetadata("orderId", orderId).build();
            PaymentIntent intent= PaymentIntent.create(params);
            return new PaymentIntentResult(intent.getId(), intent.getClientSecret());
        }catch(StripeException e){
            throw new RuntimeException("Failed to create Stripe payment intent: "+ e.getMessage(), e);
        }
    }
    @Override
    public String refund(String providerPaymentId, BigDecimal amount){
        try{
            long amountInCents= amount.multiply(BigDecimal.valueOf(100)).setScale(0,
                    RoundingMode.HALF_UP).longValueExact();
            RefundCreateParams params= RefundCreateParams.builder().setPaymentIntent(providerPaymentId)
                    .setAmount(amountInCents).build();
            Refund refund= Refund.create(params);
            return refund.getId();
        }catch(StripeException e){
            throw new RuntimeException("Failed to refund Stripe payment: "+ e.getMessage(), e);
        }
    }
    @Override
    public WebhookEventResult constructWebhookEvent(String payload, String signature){
        //returns structured result, no JSON round trip
        try {
            com.stripe.model.Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            //use jackson instead of gson
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(payload);
            String paymentIntentId = root.path("data").path("object").path("id").asText();
            if (paymentIntentId == null || paymentIntentId.isEmpty()) {
                throw new RuntimeException("Could not extract paymentIntentId from webhook payload");
            }
            return new WebhookEventResult(event.getId(), event.getType(), paymentIntentId);
        }catch (com.stripe.exception.SignatureVerificationException e) {
            throw new RuntimeException("Webhook signature verification failed", e);
        } catch (JsonProcessingException e){
            throw new RuntimeException("Webhook payload is not valid JSON", e);
        }catch (Exception e) {
            throw new RuntimeException(
                    "Webhook verification failed: " + e.getMessage(), e);
        }
    }
}
