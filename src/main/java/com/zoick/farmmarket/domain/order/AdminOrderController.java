package com.zoick.farmmarket.domain.order;
import com.zoick.farmmarket.domain.auth.FarmUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {
    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getAllOrders(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getAllOrdersPaged(page, size));
    }

    @PostMapping("/{orderId}/fulfill")
    public ResponseEntity<OrderResponse> fulfillOrder(@AuthenticationPrincipal FarmUserDetails principal,
                                                      @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.fulfillOrder(orderId, principal.getUserId()));
    }

    @PostMapping("/{orderId}/resolve-dispute")
    public ResponseEntity<OrderResponse> resolveDispute(@AuthenticationPrincipal FarmUserDetails principal,
                                                        @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.resolveDispute(orderId, principal.getUserId()));
    }
}
