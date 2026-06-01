package com.zoick.farmmarket.domain.cart;
import com.zoick.farmmarket.domain.produce.Listing;
import com.zoick.farmmarket.domain.produce.ProduceUnit;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class CartItemResponse {
    private UUID cartItemId;
    private UUID listingId;
    private String produceName;
    private ProduceUnit unit;
    private BigDecimal quantity;
    private BigDecimal retailPrice;
    private BigDecimal bulkPrice;
    private BigDecimal minBulkQuantity;
    private String currency;

    public static CartItemResponse from(CartItem cartItem){
        Listing listing= cartItem.getListing();
        return CartItemResponse.builder().cartItemId(cartItem.getId()).listingId(listing.getId()).produceName(listing.getBatch()
                .getProduce().getName()).unit(listing.getBatch().getProduce().getUnit()).quantity(cartItem.getQuantity()).retailPrice(
                        listing.getRetailPrice()).bulkPrice(listing.getBulkPrice()).minBulkQuantity(listing.getMinBulkQuantity())
                .currency(listing.getCurrency()).build();
    }
}
