package com.zoick.farmmarket.domain.delivery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/delivery-slots")
@RequiredArgsConstructor
public class DeliverySlotController {
    private final DeliverySlotService deliverySlotService;
    //admin:create a new slot
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeliverySlotResponse> createSlot(@Valid @RequestBody CreateDeliverySlotRequest request){
        return ResponseEntity.ok(deliverySlotService.createSlot(request));
    }
    //admin:view all slots
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DeliverySlotResponse>> getAllSlots(@RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE) LocalDate to){
        return ResponseEntity.ok(deliverySlotService.getAllSlotsByDateRange(from, to));
    }
    //public: view available slots by single date-> used at checkout
    @GetMapping
    public ResponseEntity<List<DeliverySlotResponse>> getAvailableSlots(@RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE) LocalDate date){
        return ResponseEntity.ok(deliverySlotService.getAvailableSlotsByDate(date));
    }
    //public: view available slots across a date range- used to show calendar view
    @GetMapping("/range")
    public ResponseEntity<List<DeliverySlotResponse>> getAvailableSlotsByRange(@RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE) LocalDate to){
        return ResponseEntity.ok(deliverySlotService.getAvailableSlotsByDateRange(from, to));
    }
}
