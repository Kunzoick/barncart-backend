package com.zoick.farmmarket.domain.produce;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateProduceRequest {
    @NotNull(message = "Category ID is required")
    private UUID categoryId;
    @NotBlank(message = "Produce name is required")
    @Size(max = 150, message = "Produce name must not exceed 150 characters")
    private String name;
    private String description;
    //@NotBlank only works on string
    @NotNull(message = "Unit is required")
   /* @Pattern(
            regexp = "(?i)KG|G|LB|PIECE",
            message = "Unit must be one of: KG, G, LB, PIECE"
    )
    */
    private ProduceUnit unit;
    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    private String imageUrl;
}
