package com.zoick.farmmarket.domain.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
// Handles all outbound email via Resend API.
// never inside a transaction boundary. If the transaction rolls back,
// no email is sent. If email fails after commit, the user can request resend.
public class EmailService {
    private final String apiKey;
    private final String fromAddress;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmailService(@Value("${resend.api-key}") String apiKey,
                        @Value("${resend.from-address:onboarding@resend.dev}") String fromAddress,
                        ObjectMapper objectMapper){
        this.apiKey= apiKey;
        this.fromAddress= fromAddress;
        this.objectMapper= objectMapper;
        this.httpClient= HttpClient.newHttpClient();
    }
    //fires after registration
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event){
     String subject= "Verify your FarmMarket account";
     String body= """
                Your FarmMarket verification code is: %s
                
                This code expires in 15 minutes.
                
                If you did not register for FarmMarket, ignore this email.
                """.formatted(event.rawCode());
        sendText(event.email(), subject, body);
    }
    //fires after password reset transaction commits
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event){
        String subject= "Reset your FarmMarket password";
        String body= """
                You requested a password reset for your FarmMarket account.
                
                Click the link below to reset your password:
                %s
                
                This link expires in 1 hour.
                
                If you did not request a password reset, ignore this email.                
                """.formatted(event.resetLink());
        sendText(event.email(), subject, body);
    }
    //sends verification code directly-> used by resend
    private void sendVerificationCode(String toEmail, String rawCode){
        String subject= "Your new FarmMarket verification code";
        String body= """
                Your FarmMarket verification code is: %s
                
                This code expires in 15 minutes.
                
                If you did not request this code, ignore this email.
                """.formatted(rawCode);
        sendText(toEmail, subject, body);
    }
    //html email-> used for order confirmation
    public void sendHtml(String toEmail, String subject, String htmlBody){
        try{
            Map<String, Object> payload= Map.of("from", fromAddress, "to", List.of(toEmail),
                    "subject", subject, "html", htmlBody);
            dispatch(toEmail, subject, payload);
        } catch (Exception e) {
            log.error("Failed to send HTML email to {}: {}", toEmail, e.getMessage());
        }
    }
    private void sendText(String toEmail, String subject, String textBody){
        try{
            Map<String, Object> payload= Map.of("from", fromAddress, "to", List.of(toEmail),
                    "subject", subject, "text", textBody);
            dispatch(toEmail, subject, payload);
        } catch (Exception e) {
            log.error("Failed to send text email to {}: {}", toEmail, e.getMessage());
        }
    }
    private void dispatch(String toEmail, String subject, Map<String, Object> payload) throws Exception{
        String jsonPayload= objectMapper.writeValueAsString(payload);
        HttpRequest request= HttpRequest.newBuilder().uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer "+ apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload)).build();
        HttpResponse<String> response= httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if(response.statusCode() >= 400){
            log.error("Resend API error for {}: status={} body={}", toEmail, response.statusCode(), response.body());
        }else{
            log.debug("Email sent to {}: {}", toEmail, subject);
        }
    }
}
