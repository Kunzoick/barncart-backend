package com.zoick.farmmarket.domain.order;
import com.zoick.farmmarket.domain.produce.ProduceUnit;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class OrderItemResponse {
    private UUID orderItemId;
    private UUID listingId;
    private UUID batchId;
    private String produceName;
    private ProduceUnit unit;
    private BigDecimal quantity;
    private BigDecimal priceAtPurchase;
    private PricingType pricingType;

    public static OrderItemResponse from(OrderItem item) {
        return OrderItemResponse.builder().orderItemId(item.getId()).listingId(item.getListing().getId())
                .batchId(item.getBatch().getId()).produceName(item.getBatch().getProduce().getName()).unit(
                        item.getBatch().getProduce().getUnit()).quantity(item.getQuantity()).priceAtPurchase(item.getPriceAtPurchase())
                .pricingType(item.getPricingType()).build();
}
}
