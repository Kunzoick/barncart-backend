package com.zoick.farmmarket.domain.auth;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
//used by /api/auth/refresh endpoint. client sends raw refresh token, we hash it and look it up in the DB.
public class RefreshRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
