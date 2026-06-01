package com.zoick.farmmarket.domain.cart;
import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CartResponse {
    private UUID cartId;
    private UUID userId;
    private List<CartItemResponse> items;
    private int itemCount;

    public static CartResponse from(Cart cart, List<CartItemResponse> items){
        return CartResponse.builder().cartId(cart.getId()).userId(cart.getUser().getId())
                .items(items).itemCount(items.size()).build();
    }
    //returns empty cart
    public static CartResponse empty(UUID userId){
        return CartResponse.builder().cartId(null).userId(userId).items(List.of())
                .itemCount(0).build();
    }
}
