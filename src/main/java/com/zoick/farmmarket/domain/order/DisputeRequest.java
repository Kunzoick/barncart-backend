package com.zoick.farmmarket.domain.order;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputeRequest(
        @NotBlank(message = "Dispute reason is required")
        @Size(max = 255, message = "Reason must be under 255 characters")
        String reason
) {}