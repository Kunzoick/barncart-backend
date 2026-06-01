package com.zoick.farmmarket.infrastructure.websocket;
import com.zoick.farmmarket.domain.produce.Listing;
import com.zoick.farmmarket.domain.produce.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryWebSocketService {
    private final SimpMessagingTemplate messagingTemplate;
    private final ListingRepository listingRepository;
    private final ApplicationEventPublisher eventPublisher;

    //Broadcasts current inventory state to all subscribes of this listing, called after any stock change
    public void publishInventoryUpdate(UUID batchId) {
        eventPublisher.publishEvent(new InventoryUpdateEvent(batchId));
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInventoryUpdate(InventoryUpdateEvent event){
        listingRepository.findByBatchIdWithFreshBatch(event.batchId()).ifPresent(listing -> {
            var batch= listing.getBatch();
            var produce= batch.getProduce();

            InventoryUpdate update= InventoryUpdate.of(listing.getId(), batch.getId(), batch.getQuantityAvailable(),
                    batch.getQuantityOriginal(), listing.getLowStockThresholdPct(), produce.getUnit(), listing.getBagWeightKg());
            String destination= "/topic/listing/"+ listing.getId()+ "/inventory";
            messagingTemplate.convertAndSend(destination, update);
            log.debug("Broadcast inventory update for listing {}",
                    listing.getId());
        });
    }
}
