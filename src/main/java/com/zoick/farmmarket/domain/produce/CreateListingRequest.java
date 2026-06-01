package com.zoick.farmmarket.domain.produce;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateListingRequest {
    @NotNull(message = "Batch ID is required")
    private UUID batchId;
    @NotNull(message = "Retail price is required")
    @DecimalMin(value = "0.01", message = "Retail price must be greater than zero")
    private BigDecimal retailPrice;
    @NotNull(message = "Bulk price is required")
    @DecimalMin(value = "0.01", message = "Bulk price must be greater than zero")
    private BigDecimal bulkPrice;
    @NotNull(message = "Minimum bulk quantity is required")
    @DecimalMin(value = "0.01", message = "Minimum bulk quantity must be greater than zero")
    private BigDecimal minBulkQuantity;
    @Min(value = 1, message = "Low stock threshold must be between 1 and 100")
    @Max(value = 100, message = "Low stock threshold must be between 1 and 100")
    private int lowStockThresholdPct = 25;
    private String currency = "CAD";
    @DecimalMin(value= "0.01", message = "Bag weight must be greater than Zero")
    private BigDecimal bagWeightKg;
}
