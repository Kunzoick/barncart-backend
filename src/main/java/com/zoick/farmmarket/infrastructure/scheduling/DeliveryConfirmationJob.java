package com.zoick.farmmarket.infrastructure.scheduling;
import com.zoick.farmmarket.domain.delivery.OrderDelivery;
import com.zoick.farmmarket.domain.delivery.OrderDeliveryRepository;
import com.zoick.farmmarket.domain.order.Order;
import com.zoick.farmmarket.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryConfirmationJob {
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final DeliveryConfirmationProcessor processor;

    @Scheduled(fixedDelay = 1800000)
    public void autoConfirmDeliveries() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        //fetch ids only -> processor loads fresh data inside its own transaction
        List<UUID> deliveryIds = orderDeliveryRepository.findPendingConfirmationBefore(cutoff);
        if (deliveryIds.isEmpty()) return;
        log.info("DeliveryConfirmationJob: auto-confirming {} deliveries", deliveryIds.size());
        for (UUID deliveryId : deliveryIds) {
            try {
                processor.confirmOne(deliveryId);
                //log delivery id and not orderId
                log.debug("Auto-confirmed delivery {}", deliveryId);
            } catch (Exception e) {
                log.error("Failed to auto-confirm delivery {}: {}", deliveryId, e.getMessage());
            }
        }
        log.info("DeliveryConfirmationJob: completed auto-confirming {} deliveries", deliveryIds.size());
    }
}
