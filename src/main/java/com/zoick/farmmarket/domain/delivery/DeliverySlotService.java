package com.zoick.farmmarket.domain.delivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliverySlotService {
    private final DeliverySlotRepository deliverySlotRepository;
    //admin: create a new delivery slot
    @Transactional
    public DeliverySlotResponse createSlot(CreateDeliverySlotRequest request){
        boolean exists= deliverySlotRepository.findBySlotDateAndSlotType(request.slotDate(), request.slotType())
                .isPresent();
        if(exists){
            throw new IllegalArgumentException("A "+ request.slotType()+ " slot already exists for "+
                    request.slotDate());
        }
        DeliverySlot slot =  new DeliverySlot();
        slot.setSlotDate(request.slotDate());
        slot.setSlotType(request.slotType());
        slot.setCapacity(request.capacity());
        return DeliverySlotResponse.from(deliverySlotRepository.save(slot));
    }
    //public(available slots for a single date-> filtered at DB level
    @Transactional(readOnly = true)
    public List<DeliverySlotResponse> getAvailableSlotsByDate(LocalDate date){
        return deliverySlotRepository.findAvailableBySlotDate(date).stream().map(DeliverySlotResponse::from)
                .toList();
    }
    //public(available slots across a date range-> used by checkout calendar
    @Transactional(readOnly = true)
    public List<DeliverySlotResponse> getAvailableSlotsByDateRange(LocalDate from, LocalDate to){
        LocalDate today= LocalDate.now();
        LocalDate effectiveFrom= from.isBefore(today) ? today : from;
        return deliverySlotRepository.findAvailableBySlotDateBetween(effectiveFrom, to).stream().map(DeliverySlotResponse::from)
                .toList();
    }
    //admin(all slot including full ones across a date range)
    @Transactional(readOnly = true)
    public List<DeliverySlotResponse> getAllSlotsByDateRange(LocalDate from, LocalDate to){
        return deliverySlotRepository.findAllBySlotDateBetween(from, to).stream().map(DeliverySlotResponse::from)
                .toList();
    }
    //internal(single entry point for booking a slot(called by webhookService on Paid
    @Transactional
    public DeliverySlot bookSlot(UUID slotId){
        DeliverySlot slot = deliverySlotRepository.findById(slotId).orElseThrow(() -> new IllegalArgumentException(
                "Delivery slot not found: "+ slotId));
        int updated= deliverySlotRepository.incrementBookedCount(slotId);
        if(updated == 0){
            throw new IllegalStateException("Delivery slot is fully booked: "+ slot.getSlotDate() + " "+ slot.getSlotType());
        }
        return slot;
    }
    //releases a slot booking -> called by OrderService on cancellation
    @Transactional
    public void releaseSlot(UUID slotId){
        int updated= deliverySlotRepository.decrementBookedCount(slotId);
        if(updated == 0){
            log.warn("decrementBookedCount returned 0 for slot {} - slot may already be at 0", slotId);
        }
    }
}
