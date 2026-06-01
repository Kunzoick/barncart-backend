package com.zoick.farmmarket.domain.produce;
import java.util.UUID;
//dto for produce category so the controller never exposes the raw entity
public record ProduceCategoryResponse(UUID id, String name, int refundWindowDays){
    public static ProduceCategoryResponse from(ProduceCategory category){
        return new ProduceCategoryResponse(category.getId(), category.getName(),
                category.getRefundWindowDays());
    }
}
