package com.zoick.farmmarket.domain.cart;
import com.zoick.farmmarket.domain.auth.FarmUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

//Customer-facing controller
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CartController {
    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal FarmUserDetails principal){
        return ResponseEntity.ok(cartService.getCart(principal.getUserId()));
    }
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@AuthenticationPrincipal FarmUserDetails principal, @Valid @RequestBody
    AddCartItemRequest request){
        return ResponseEntity.ok(cartService.addItem(principal.getUserId(), request));
    }
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateItem(@AuthenticationPrincipal FarmUserDetails principal, @PathVariable UUID cartItemId,
                                                   @Valid @RequestBody UpdateCartItemRequest request){
        return ResponseEntity.ok(cartService.updateItem(principal.getUserId(), cartItemId, request));
    }
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> removeItem(@AuthenticationPrincipal FarmUserDetails principal, @PathVariable UUID cartItemId){
        return ResponseEntity.ok(cartService.removeItem(principal.getUserId(), cartItemId));
    }
    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal FarmUserDetails principal){
        cartService.clearCart(principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
