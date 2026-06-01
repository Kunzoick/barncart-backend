package com.zoick.farmmarket.domain.produce;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
//the field names matches the websocket broadcast fields so frontend logic is identical for both HTTP & Websocket
public class ListingResponse {
    private UUID listingId;
    private UUID batchId;
    //produce info
    private String produceName;
    private String category;
    private ProduceUnit unit;
    private BigDecimal bagWeightKg;
    private String imageUrl;
    //pricing
    private BigDecimal retailPrice;
    private BigDecimal bulkPrice;
    private BigDecimal minBulkQuantity;
    private String currency;
    //batch info
    private LocalDate harvestedAt;
    private LocalDate expiryDate;
    private String batchStatus;
    //Inventory-> matches websocket broadcast filed
    private BigDecimal quantityAvailable;
    private BigDecimal quantityOriginal;
    private BigDecimal percentageRemaining;
    private boolean lowStock;
    //factory method-> it builds response from entities
    public static ListingResponse from(Listing listing){
        HarvestBatch batch =  listing.getBatch();
        Produce produce= batch.getProduce();
        BigDecimal percentageRemaining= BigDecimal.ZERO;
        if(batch.getQuantityOriginal().compareTo(BigDecimal.ZERO) >0){
            percentageRemaining= batch.getQuantityAvailable().multiply(BigDecimal.valueOf(100))
                    .divide(batch.getQuantityOriginal(), 2, RoundingMode.HALF_UP);
        }
        boolean isLowStock = percentageRemaining.compareTo(BigDecimal.valueOf(listing.getLowStockThresholdPct())) < 0;
        return ListingResponse.builder().listingId(listing.getId()).batchId(batch.getId()).produceName(produce.getName())
                .category(produce.getCategory().getName()).unit(produce.getUnit()).bagWeightKg(listing.getBagWeightKg())
                .imageUrl(produce.getImageUrl()).retailPrice(listing.getRetailPrice()).bulkPrice(listing.getBulkPrice())
                .minBulkQuantity(listing.getMinBulkQuantity()).currency(listing.getCurrency()).harvestedAt(batch.getHarvestedAt()).
                expiryDate(batch.getExpiryDate()).batchStatus(batch.getStatus().toString()).quantityAvailable(batch.getQuantityAvailable())
                .quantityOriginal(batch.getQuantityOriginal()).percentageRemaining(percentageRemaining).lowStock(isLowStock).build();
    }
}
