package com.zoick.farmmarket.domain.auth;
import com.zoick.farmmarket.domain.user.Role;
import com.zoick.farmmarket.domain.user.User;
import com.zoick.farmmarket.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
/*
Contains all auth business logic. Registration(creates and then issues token), login, refresh tokens
Refresh validates the stored hash, revokes the old token and issues a new pair. Logout revokes all tokens
 */
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;
    private final PasswordResetRepository passwordResetRepository;
    private final EmailVerificationRepository emailVerificationRepository;

    //frontend url password reset link
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setRole(Role.CUSTOMER);
        user.setActive(true);
        userRepository.save(user);
        //generate and save verification code & if transaction rolls back, code is never saved and email never sends
        String rawCode = generateSixDigitCode();
        String codeHash = jwtUtil.hashWithSha256(rawCode);
        EmailVerification verification = new EmailVerification();
        verification.setUserId(user.getId());
        verification.setCodeHash(codeHash);
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        emailVerificationRepository.save(verification);
        //publish event
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), rawCode));
        return new MessageResponse("Registration successful. Check your email for a verification code.");
    }
    @Transactional
    public AuthResponse login(LoginRequest request){
        //throws authenticationException if credentials are wrong
        Authentication authentication= authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
       FarmUserDetails principal= (FarmUserDetails) authentication.getPrincipal();
        //revoke all existing refresh tokens on new login
        refreshTokenRepository.revokeAllByUserId(principal.getUserId());
        return issueTokenPair(principal);
    }
    @Transactional
    public AuthResponse refresh(RefreshRequest request){
        String hash= jwtUtil.hashWithSha256(request.getRefreshToken());
        RefreshToken stored= refreshTokenRepository.findByTokenHash(hash).orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if(stored.isRevoked()){
            throw new IllegalArgumentException("Refresh token has been revoked");
        }
        if(stored.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new IllegalArgumentException("Refresh token has expired");
        }
        //Revoke current token — rotate on every refresh
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        FarmUserDetails principal= new FarmUserDetails(stored.getUser());
        return issueTokenPair(principal);
    }
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = jwtUtil.hashWithSha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }
    // verify email -> activates user
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request){
        User user= userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new IllegalArgumentException("invalid verification attempt"));
        String codeHash= jwtUtil.hashWithSha256(request.getCode());
        EmailVerification verification= emailVerificationRepository.findByUserId(user.getId()).orElseThrow(() ->
                new IllegalArgumentException("No verification code found. Please request a new one"));
        if(!verification.getCodeHash().equals(codeHash)){
            throw new IllegalArgumentException("Invalid verification code");
        }
        if(verification.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new IllegalArgumentException("Verification code has expired.please request a new one.");
        }
        user.setActive(true);
        userRepository.save(user);
        emailVerificationRepository.deleteByUserId(user.getId());
        FarmUserDetails principal= new FarmUserDetails(user);
        return issueTokenPair(principal);
    }
    //resend verification-> replaces existing code in the same transaction
    //always return 200-> never reveal if email exists
    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request){
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            if(!user.isActive()){
                emailVerificationRepository.deleteByUserId(user.getId());
                String rawCode= generateSixDigitCode();
                String codeHash= jwtUtil.hashWithSha256(rawCode);
                EmailVerification verification= new EmailVerification();
                verification.setUserId(user.getId());
                verification.setCodeHash(codeHash);
                verification.setExpiresAt(LocalDateTime.now().plusMinutes(30));
                emailVerificationRepository.save(verification);
                eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), rawCode));
            }
        });
        return new MessageResponse("If your email is registered and unverified, a new code has been sent to your email");
    }
    //forgot password
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request){
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            passwordResetRepository.deleteByUserId(user.getId());
            String rawToken= jwtUtil.generateRawRefreshToken();
            String tokenHash= jwtUtil.hashWithSha256(rawToken);
            PasswordReset reset= new PasswordReset();
            reset.setUserId(user.getId());
            reset.setTokenHash(tokenHash);
            reset.setExpiresAt(LocalDateTime.now().plusHours(1));
            passwordResetRepository.save(reset);
            //configure frontend url
            String resetLink= frontendUrl + "/reset-password?token=" + rawToken;
            eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), resetLink));
        });
        return new MessageResponse("If your email is registered, a password reset link has been sent.");
    }
    //reset password
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request){
        String tokenHash= jwtUtil.hashWithSha256(request.getToken());
        PasswordReset reset= passwordResetRepository.findByTokenHash(tokenHash).orElseThrow(() -> new IllegalArgumentException("Invalid/expired reset token"));
        if(reset.getExpiresAt().isBefore(LocalDateTime.now())){
            passwordResetRepository.delete(reset);
            throw new IllegalArgumentException("Reset token has expired");
        }
        User user= userRepository.findById(reset.getUserId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        passwordResetRepository.deleteByUserId(user.getId());
        return new MessageResponse("Password reset successful. Please log in with your new password.");
    }
    //change password-> requires current password verification(revokes all refresh tokens)
    @Transactional
    public MessageResponse changePassword(UUID userId, ChangePasswordRequest request){
        User user= userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        //verify current password before allowing change
        if(!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())){
            throw new IllegalArgumentException("Current password is incorrect");
        }
        //prevent changing to the same password
        if(passwordEncoder.matches(request.newPassword(), user.getPasswordHash())){
            throw new IllegalArgumentException("New password cannot be the same as the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        //revoke all refresh tokens and require re-login
        refreshTokenRepository.revokeAllByUserId(user.getId());
        return new MessageResponse("Password changed successfully. Please log in again.");
    }
    private String generateSixDigitCode(){
        int code= new SecureRandom().nextInt(900000)+ 100000;
        return String.valueOf(code);
    }
    //Shared token issuance-> used by register, login and refresh
    private AuthResponse issueTokenPair(FarmUserDetails principal){
        String accessToken= jwtUtil.generateAccessToken(principal.getEmail(), principal.getRole().name());
        String rawRefreshToken= jwtUtil.generateRawRefreshToken();
        String tokenHash= jwtUtil.hashWithSha256(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken();
        //load user reference for FK- one targeted hit only during register/refresh
        User userRef= userRepository.getReferenceById(principal.getUserId());
        refreshToken.setUser(userRef);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(jwtUtil.refreshTokenExpiry());
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken, principal.getEmail(), principal.getRole().name(), principal.getFirstName());
    }
    //called by controller after extracting token from httpOnly cookie
    @Transactional
    public AuthResponse refreshFromToken(String rawRefreshToken){
        String hash= jwtUtil.hashWithSha256(rawRefreshToken);
        RefreshToken stored= refreshTokenRepository.findByTokenHash(hash).orElseThrow(() -> new IllegalArgumentException("" +
                "Invalid refresh token"));
        if(stored.isRevoked()){
            throw new IllegalArgumentException("Refresh token has been revoked");
        }
        if(stored.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new IllegalArgumentException("Refresh token has expired");
        }
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        FarmUserDetails principal= new FarmUserDetails(stored.getUser());
        return issueTokenPair(principal);
    }
}
