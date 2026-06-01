package com.zoick.farmmarket.domain.order;
import com.zoick.farmmarket.domain.auth.EmailService;
import com.zoick.farmmarket.domain.delivery.OrderDelivery;
import com.zoick.farmmarket.domain.delivery.OrderDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
// Listens for OrderConfirmedEvent after commit, queries fresh order data,
// builds HTML confirmation email, and delegates sending to EmailService.
// Lives in order package — owns order-specific email content logic.
public class OrderConfirmationEmailService {
    private final EmailService emailService;
    private final OrderRepository orderRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final OrderItemRepository orderItemRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event){
        try{
            Order order= orderRepository.findById(event.orderId()).orElseThrow(() -> new IllegalArgumentException(
                    "Order not found for confirmation email: "+ event.orderId()));
            List<OrderItem> items = orderItemRepository.findAllByOrderIdWithDetails(order.getId());
            OrderDelivery delivery= orderDeliveryRepository.findByOrderId(order.getId()).orElse(null);
            String html= buildConfirmationHtml(order, items, delivery);
            emailService.sendHtml(event.userEmail(), "Your FarmMarket order is confirmed", html);
        } catch (Exception e) {
            log.error("Failed to send order confirmation email {}: {}", event.orderId(), e.getMessage());
            //don't rethrow-> email failure must not affect order state
        }
    }
    private String buildConfirmationHtml(Order order, List<OrderItem> items, OrderDelivery delivery){
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <html>
                <body style="font-family: Arial, sans-serif; color: #1A1A1A; max-width: 600px; margin: 0 auto; padding: 24px;">
                """);

        // Header
        sb.append("""
                <h2 style="color: #2D6A4F; border-bottom: 2px solid #2D6A4F; padding-bottom: 8px;">
                    FarmMarket Order Confirmation
                </h2>
                """);

        // Order meta
        sb.append(String.format("""
                <p><strong>Order ID:</strong> %s</p>
                <p><strong>Placed:</strong> %s</p>
                """,
                order.getId(),
                order.getCreatedAt().format(
                        DateTimeFormatter.ofPattern("MMMM d, yyyy"))));

        // Items
        sb.append("""
                <h3 style="color: #2D6A4F; margin-top: 24px;">Items</h3>
                <table style="width: 100%; border-collapse: collapse;">
                <tr style="border-bottom: 1px solid #ccc;">
                    <th style="text-align: left; padding: 8px 0;">Item</th>
                    <th style="text-align: right; padding: 8px 0;">Price</th>
                </tr>
                """);

        for (OrderItem item : items) {
            BigDecimal lineTotal = item.getPriceAtPurchase()
                    .multiply(item.getQuantity());
            sb.append(String.format("""
                    <tr style="border-bottom: 1px solid #eee;">
                        <td style="padding: 8px 0;">%s × %s %s</td>
                        <td style="text-align: right; padding: 8px 0;">$%s</td>
                    </tr>
                    """,
                    item.getListing().getBatch().getProduce().getName(),
                    item.getQuantity().stripTrailingZeros().toPlainString(),
                    item.getListing().getBatch().getProduce().getUnit().name(),
                    lineTotal.setScale(2, java.math.RoundingMode.HALF_UP)));
    }
        sb.append("</table>");

        // Total
        sb.append(String.format("""
                <p style="font-size: 18px; font-weight: bold; color: #2D6A4F; margin-top: 16px;">
                    Total: $%s %s
                </p>
                """,
                order.getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP),
                order.getCurrency()));

        // Delivery
        if (delivery != null) {
            String dayName = delivery.getDeliverySlot().getSlotDate()
                    .getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            String slotDate = delivery.getDeliverySlot().getSlotDate()
                    .format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
            String slotType = delivery.getDeliverySlot().getSlotType().name();
            String slotDisplay = slotType.charAt(0)
                    + slotType.substring(1).toLowerCase();

            sb.append(String.format("""
                    <h3 style="color: #2D6A4F; margin-top: 24px;">Delivery</h3>
                    <p><strong>%s, %s — %s slot</strong></p>
                    <p>%s</p>
                    """,
                    dayName, slotDate, slotDisplay,
                    formatAddress(delivery)));
        }

        // Footer
        sb.append("""
                <p style="margin-top: 32px; color: #666; font-size: 14px;">
                    Thank you for your order. If you have any questions,
                    reply to this email.
                </p>
                </body>
                </html>
                """);

        return sb.toString();
    }
    private String formatAddress(OrderDelivery delivery){
     StringBuilder address = new StringBuilder();
     address.append(delivery.getAddressLine1());
     if(delivery.getAddressLine2() != null && !delivery.getAddressLine2().isBlank()){
         address.append(", ").append(delivery.getAddressLine2());
     }
     address.append("<br>").append(delivery.getCity()).append(", ").append(delivery.getProvince()).append(" ")
             .append(delivery.getPostalCode());
     return address.toString();
    }
}
