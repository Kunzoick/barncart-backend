package com.zoick.farmmarket.infrastructure.scheduling;
import com.zoick.farmmarket.domain.audit.AuditService;
import com.zoick.farmmarket.domain.delivery.OrderDelivery;
import com.zoick.farmmarket.domain.delivery.OrderDeliveryRepository;
import com.zoick.farmmarket.domain.order.Order;
import com.zoick.farmmarket.domain.order.OrderRepository;
import com.zoick.farmmarket.domain.order.OrderStatus;
import com.zoick.farmmarket.infrastructure.websocket.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryConfirmationProcessor {
   private final OrderDeliveryRepository orderDeliveryRepository;
   private final OrderRepository orderRepository;
   private final ApplicationEventPublisher eventPublisher;
   private final AuditService auditService;
   //each delivery gets its own transaction
    @Transactional
    public void confirmOne(UUID deliveryId) {
        OrderDelivery delivery= orderDeliveryRepository.findByIdWithOrderAndUser(deliveryId).orElseThrow(() -> new IllegalStateException(
                "Delivery not found: "+ deliveryId));
        delivery.setAutoConfirmed(true);
        orderDeliveryRepository.save(delivery);

        Order order= delivery.getOrder();
        if(order.getStatus() == OrderStatus.FULFILLED){
            String previousStatus= order.getStatus().name();
            order.setStatus(OrderStatus.DELIVERED);
            orderRepository.save(order);
            auditService.log("Order", order.getId(), "ORDER_AUTO_CONFIRMED", null,
                    Map.of("status", previousStatus),
                    Map.of("status", OrderStatus.DELIVERED.name(),
                            "confirmedBy", "SYSTEM"));
            eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getUser().getId(), order.getId(), previousStatus,
                    OrderStatus.DELIVERED.name()));
            log.info("Auto-confirmed delivery for order {}", order.getId());
        }
    }
}
