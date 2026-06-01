package com.zoick.farmmarket.domain.order;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CheckoutRequest {
    //@NotNull(message = "Idempotency key is required")
    @NotBlank(message = "Idempotency key must not be blank")
    private String idempotencyKey;
    @NotNull(message = "Delivery slot is required")
    private UUID deliverySlotId;
    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255)
    private String addressLine1;
    @Size(max = 255)
    private String addressLine2;
    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;
    @NotBlank(message = "Province is required")
    @Size(max = 100)
    private String province;
    @NotBlank(message = "Postal code is required")
    @Size(max = 20)
    private String postalCode;
    @NotBlank(message = "Country not required")
    @Size(min = 2, max = 3, message = "Country must be a valid country code")
    private String country = "CA";
    private String deliveryNotes;
}