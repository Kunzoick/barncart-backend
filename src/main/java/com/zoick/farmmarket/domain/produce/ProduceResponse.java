package com.zoick.farmmarket.domain.produce;
import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Getter
@Builder
public class ProduceResponse {
    private UUID id;
    private String name;
    private String description;
    private ProduceUnit unit;
    private boolean active;
    private UUID categoryId;
    private String categoryName;
    private String imageUrl;

    public static ProduceResponse from(Produce produce){
        return ProduceResponse.builder().id(produce.getId()).name(produce.getName()).description(produce.getDescription())
                .unit(produce.getUnit()).active(produce.isActive()).categoryId(produce.getCategory().getId()).categoryName(
                        produce.getCategory().getName()).imageUrl(produce.getImageUrl()).build();
    }
}
