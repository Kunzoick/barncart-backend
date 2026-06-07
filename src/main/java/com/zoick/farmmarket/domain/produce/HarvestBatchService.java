package com.zoick.farmmarket.domain.produce;
import com.zoick.farmmarket.domain.audit.AuditService;
import com.zoick.farmmarket.domain.order.*;
import com.zoick.farmmarket.infrastructure.websocket.InventoryUpdateEvent;
import com.zoick.farmmarket.infrastructure.websocket.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
/*
This is where the check constraint enforcement lives. Three cross-filed rules enforced here
it expires after harvest, quantity must be positive and batch cancellation must cascade to active reservations
 */
public class HarvestBatchService {
    private final HarvestBatchRepository harvestBatchRepository;
    private final ProduceRepository produceRepository;
    private final ReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public HarvestBatch getRawBatchById(UUID id){
        return harvestBatchRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Batch not found: "+ id));
    }
    @Transactional(readOnly = true)
    public HarvestBatchResponse getBatchById(UUID id){
        return HarvestBatchResponse.from(getRawBatchById(id));
    }
    @Transactional(readOnly = true)
    public List<HarvestBatchResponse> getBatchesByProduce(UUID produceId){
        return harvestBatchRepository.findAllByProduceId(produceId).stream().map(HarvestBatchResponse::from).collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public List<HarvestBatchResponse> getActiveBatches(){
        return harvestBatchRepository.findAllByStatus(BatchStatus.ACTIVE).stream().map(HarvestBatchResponse::from).collect(Collectors.toList());
    }
    @Transactional
    public HarvestBatchResponse createBatch(CreateHarvestBatchRequest request){
        if(!request.getExpiryDate().isAfter(request.getHarvestedAt())){
            throw new IllegalArgumentException("Expiry date must be after harvest date");
        }
        Produce produce= produceRepository.findById(request.getProduceId()).orElseThrow(() -> new IllegalArgumentException(
                "Produce not found: "+ request.getProduceId()));
        HarvestBatch batch = new HarvestBatch();
        batch.setProduce(produce);
        batch.setQuantityOriginal(request.getQuantityOriginal());
        // quantityAvailable starts equal to quantityOriginal
        batch.setQuantityAvailable(request.getQuantityOriginal());
        batch.setHarvestedAt(request.getHarvestedAt());
        batch.setExpiryDate(request.getExpiryDate());
        batch.setNotes(request.getNotes());
        batch.setStatus(BatchStatus.ACTIVE);
        return HarvestBatchResponse.from(harvestBatchRepository.save(batch));
    }
    @Transactional
    public HarvestBatchResponse cancelBatch(UUID batchId, UUID adminId){
        HarvestBatch batch =  getRawBatchById(batchId);
        if(batch.getStatus() != BatchStatus.ACTIVE){
            throw new IllegalArgumentException("Only ACTIVE batches can be cancelled");
        }
        //cancel all active reservations on this batch and stock is zeored
        List<Reservation> activeReservations= reservationRepository.findAllByBatchIdAndStatus(batchId, ReservationStatus.ACTIVE);
        for(Reservation reservation : activeReservations){
            //return stock first with upper bound
            int returned= harvestBatchRepository.returnStock(reservation.getBatch().getId(), reservation.getQuantity());
            if(returned==0){
                log.warn("returnStock returned 0 for batch {} reservation {} - possible double return", batchId, reservation.getId());
            }else{
                auditService.log("HarvestBatch", batchId, "STOCK_RETURNED", adminId,
                        Map.of("reason", "BATCH_CANCELLED"),
                        Map.of("quantityReturned", reservation.getQuantity()));
            }
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);
            //cascade cancel to order which prevents order stuck in reserved permanently
            Order order= reservation.getOrder();
            String previousStatus = order.getStatus().name();
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancellationReason("Batch cancelled by admin");
            orderRepository.save(order);
            //notify each affected customer via websocket
            eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getUser().getId(), order.getId(),
                    previousStatus, OrderStatus.CANCELLED.name()));
        }
        batch.setStatus(BatchStatus.CANCELLED);
        batch.setQuantityAvailable(BigDecimal.ZERO);
        harvestBatchRepository.save(batch);
        auditService.log("HarvestBatch", batchId, "BATCH_CANCELLED", adminId,
                Map.of("status", BatchStatus.ACTIVE.name()),
                Map.of("status", BatchStatus.CANCELLED.name(),
                        "affectedReservations", activeReservations.size()));

        eventPublisher.publishEvent(new InventoryUpdateEvent(batchId));
        return HarvestBatchResponse.from(batch);
    }
    @Transactional
    public HarvestBatchResponse restockBatch(UUID batchId, BigDecimal quantity, UUID adminId){
        if(quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Restock quantity must be greater than Zero");
        }
        HarvestBatch batch= getRawBatchById(batchId);
        if(batch.getStatus() == BatchStatus.CANCELLED){
            throw new IllegalArgumentException("Cancelled batches cannot be restocked");
        }
        BigDecimal previousAvailable = batch.getQuantityAvailable();
        BigDecimal previousOriginal= batch.getQuantityOriginal();
        int rows= harvestBatchRepository.addStock(batchId, quantity);
        if(rows == 0){
            throw new IllegalStateException("Restock failed - batch may have been cancelled");
        }
        if(batch.getStatus() == BatchStatus.DEPLETED){
            batch.setStatus(BatchStatus.ACTIVE);
            harvestBatchRepository.save(batch);
        }
        HarvestBatch updated= getRawBatchById(batchId);
        auditService.log("HarvestBatch", batchId, "BATCH_RESTORED", adminId,
                Map.of("quantityAvailable", previousAvailable, "quantityOriginal", previousOriginal),
                Map.of("quantityAdded", quantity, "quantityAvailable", updated.getQuantityAvailable(),
                        "quantityOriginal", updated.getQuantityOriginal()));
        eventPublisher.publishEvent(new InventoryUpdateEvent(batchId));
        return HarvestBatchResponse.from(updated);
    }
}
