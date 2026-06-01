package com.zoick.farmmarket.infrastructure.scheduling;
import com.zoick.farmmarket.domain.order.Order;
import com.zoick.farmmarket.domain.order.OrderRepository;
import com.zoick.farmmarket.domain.order.OrderStatus;
import com.zoick.farmmarket.domain.order.Reservation;
import com.zoick.farmmarket.domain.order.ReservationRepository;
import com.zoick.farmmarket.domain.order.ReservationStatus;
import com.zoick.farmmarket.domain.produce.HarvestBatchRepository;
import com.zoick.farmmarket.infrastructure.websocket.InventoryWebSocketService;
import com.zoick.farmmarket.infrastructure.websocket.OrderStatusWebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationExpiryProcessor {
    private final ReservationRepository reservationRepository;
    private final HarvestBatchRepository harvestBatchRepository;
    private final OrderRepository orderRepository;
    private final InventoryWebSocketService inventoryWebSocketService;
    private final OrderStatusWebSocketService orderStatusWebSocketService;

    //each reservation gets its own transaction & one failure rolls back only that reservation
    @Transactional
    public void expireOne(UUID reservationId) {
        //reload everything fresh inside this transaction
        //the job passes only the id-> no stale proxies from a dead session
        Reservation reservation= reservationRepository.findById(reservationId).orElseThrow(() ->
                new IllegalStateException("Reservation not found: "+ reservationId));
        harvestBatchRepository.returnStock(reservation.getBatch().getId(), reservation.getQuantity());
        reservation.setStatus(ReservationStatus.EXPIRED);
        reservationRepository.save(reservation);
        //broadcast inventory update
        inventoryWebSocketService.publishInventoryUpdate(reservation.getBatch().getId());
        //reload order
        Order order= orderRepository.findByIdWithUser(reservation.getOrder().getId()).orElseThrow(() ->
                new IllegalStateException("Order not found for reservation: "+ reservationId));
        if(order.getStatus() == OrderStatus.RESERVED){
            order.setStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);
            //broadcast to customer
            orderStatusWebSocketService.publishOrderStatusUpdate(order.getUser().getId(), order.getId(),
                    OrderStatus.RESERVED.name(), OrderStatus.EXPIRED.name());
        }
    }
}
