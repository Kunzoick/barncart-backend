package com.zoick.farmmarket.domain.auth;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
//thin layer-> receives requests, delegates to service layer, returns responses
public class AuthController {
    private final AuthService authService;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;
    private static final String REFRESH_COOKIE = "refreshToken";
    private static final int SEVEN_DAYS= 7 * 24 * 60 * 60;

    //sets httpOnly cookie with refresh token
    private void setRefreshCookie(HttpServletResponse response, String rawRefreshToken){
        Cookie cookie= new Cookie(REFRESH_COOKIE, rawRefreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(SEVEN_DAYS);
        response.addCookie(cookie);
    }
    //clears the refresh cookie
    private void clearRefreshCookie(HttpServletResponse response){
        Cookie cookie= new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
    private String extractRefreshCookie(HttpServletRequest request){
        if(request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> REFRESH_COOKIE.equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse authResponse =  authService.login(request);
        setRefreshCookie(response, authResponse.getRefreshToken());
        //return response without refresh token in body(now it is in cookie)
        return ResponseEntity.ok(new AuthResponse(authResponse.getAccessToken(), null, authResponse.getEmail(),
                authResponse.getRole(), authResponse.getFirstName()));
    }
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken= extractRefreshCookie(request);
        if(rawToken == null){
            return ResponseEntity.status(401).build();
        }
        AuthResponse authResponse= authService.refreshFromToken(rawToken);
        setRefreshCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(new AuthResponse(authResponse.getAccessToken(), null, authResponse.getEmail(),
                authResponse.getRole(), authResponse.getFirstName()));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String rawToken= extractRefreshCookie(request);
        if(rawToken != null){
            authService.logout(rawToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletResponse response){
        AuthResponse auth= authService.verifyEmail(request);
        setRefreshCookie(response, auth.getRefreshToken());
        return ResponseEntity.ok(new AuthResponse(auth.getAccessToken(), null, auth.getEmail(),
                auth.getRole(), auth.getFirstName()));
    }
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request){
        return ResponseEntity.ok(authService.resendVerification(request));
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request){
        return ResponseEntity.ok(authService.forgotPassword(request));
    }
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request){
        return ResponseEntity.ok(authService.resetPassword(request));
    }
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> changePassword(@AuthenticationPrincipal FarmUserDetails principal, @Valid
                                                          @RequestBody ChangePasswordRequest request){
        return ResponseEntity.ok(authService.changePassword(principal.getUserId(), request));
    }
}
