package com.zoick.farmmarket.domain.produce;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class HarvestBatchResponse {
    private UUID id;
    private UUID produceId;
    private String produceName;
    private BigDecimal quantityOriginal;
    private BigDecimal quantityAvailable;
    private LocalDate harvestedAt;
    private LocalDate expiryDate;
    private String status;
    private String notes;

    public static HarvestBatchResponse from(HarvestBatch batch){
        return HarvestBatchResponse.builder().id(batch.getId()).produceId(batch.getProduce().getId()).produceName(batch.getProduce().getName())
                .quantityOriginal(batch.getQuantityOriginal()).quantityAvailable(batch.getQuantityAvailable()).harvestedAt(batch.getHarvestedAt())
                .expiryDate(batch.getExpiryDate()).status(batch.getStatus().toString()).notes(batch.getNotes()).build();
    }
}
