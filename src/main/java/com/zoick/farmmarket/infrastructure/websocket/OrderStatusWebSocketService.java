package com.zoick.farmmarket.infrastructure.websocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
//called when order status transitions, sends private message to the specific user who owns the order
public class OrderStatusWebSocketService {
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    //publishes an event inside a transaction, websocket message fires after transaction commits
    //call this form inside @Transactional methods
    public void publishOrderStatusUpdate(UUID userId, UUID orderId, String previousStatus,
                                         String currentStatus){
        eventPublisher.publishEvent(new OrderStatusChangedEvent(userId, orderId, previousStatus, currentStatus));
    }
    //fires after transaction commits, send private message to the specific user
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event){
        OrderStatusUpdate update= new OrderStatusUpdate(event.orderId(), event.previousStatus(), event.currentStatus(),
                LocalDateTime.now());
        messagingTemplate.convertAndSendToUser(event.userId().toString(), "/queue/orders", update);
        log.debug("Sent order status update to user {} - {} -> {}", event.userId(), event.previousStatus(), event.currentStatus());
    }
}
