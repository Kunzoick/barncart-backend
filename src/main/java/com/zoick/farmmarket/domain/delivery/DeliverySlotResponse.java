package com.zoick.farmmarket.domain.delivery;
import java.time.LocalDate;
import java.util.UUID;

public record DeliverySlotResponse(UUID id, LocalDate slotDate, SlotType slotType,
                                   int capacity, int bookedCount, boolean available) {
    public static DeliverySlotResponse from(DeliverySlot slot){
        return new DeliverySlotResponse(slot.getId(), slot.getSlotDate(), slot.getSlotType(),
                slot.getCapacity(), slot.getBookedCount(), slot.getBookedCount() < slot.getCapacity());
    }
}
