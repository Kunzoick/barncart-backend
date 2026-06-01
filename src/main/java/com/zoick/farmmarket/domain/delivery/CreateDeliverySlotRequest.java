package com.zoick.farmmarket.domain.delivery;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
//inbound DTO for admin slot creation
public record CreateDeliverySlotRequest(
        @NotNull(message = "Slot date is required")
        @Future(message = "Slot date must be in the future")
        LocalDate slotDate,
        @NotNull(message = "Slot type is required")
        SlotType slotType,
        @Positive(message = "Capacity must be greater than zero")
        int capacity
) {}
