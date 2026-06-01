package com.zoick.farmmarket.infrastructure.websocket;
import com.zoick.farmmarket.domain.produce.ProduceUnit;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Getter
@Builder
//payload sent on every stock change
public class InventoryUpdate {
    private UUID listingId;
    private UUID batchId;
    private BigDecimal quantityAvailable;
    private BigDecimal quantityOriginal;
    private BigDecimal percentageRemaining;
    private boolean lowStock;
    private ProduceUnit unit;
    private BigDecimal bagWeightKg;

    public static InventoryUpdate of(
            UUID listingId, UUID batchId, BigDecimal quantityAvailable, BigDecimal quantityOriginal,
            int lowStockThresholdPct, ProduceUnit unit, BigDecimal bagWeightKg){
        BigDecimal percentage= BigDecimal.ZERO;
        if(quantityOriginal.compareTo(BigDecimal.ZERO) >0){
            percentage= quantityAvailable.multiply(BigDecimal.valueOf(100))
                    .divide(quantityOriginal, 2, RoundingMode.HALF_UP);
        }
        boolean lowStock= percentage.compareTo(BigDecimal.valueOf(lowStockThresholdPct)) < 0;
        return InventoryUpdate.builder().listingId(listingId).batchId(batchId).quantityAvailable(quantityAvailable)
                .quantityOriginal(quantityOriginal).percentageRemaining(percentage).lowStock(lowStock).unit(unit)
                .bagWeightKg(bagWeightKg).build();
    }
}
