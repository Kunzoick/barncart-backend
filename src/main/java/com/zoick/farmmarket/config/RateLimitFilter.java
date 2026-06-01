package com.zoick.farmmarket.config;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zoick.farmmarket.domain.auth.JwtUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
/*
registered explicitly in security config at HIGHEST_PRECEDENCE, so it runs before Spring security processes
the request
 */
public class RateLimitFilter extends OncePerRequestFilter{
    private final JwtUtil jwtUtil;

    //checkout: 5 per minute- a real user initiates checkout once, not repeatedly
    //eviction window 10mins-> longer than the 1min refill window
    private final Cache<String, Bucket> checkoutCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(5_000).build();
    //login: 10 per minute(eviction window 1hr)
    private final Cache<String, Bucket> loginCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS).maximumSize(10_000).build();
    //Registration: 3 per hour-> strict limit, account creation abuse is severe
    //eviction window 2hrs
    private final Cache<String, Bucket> registerCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS).maximumSize(5_000).build();
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException{
        String path= request.getRequestURI();
        String method= request.getMethod();
        if(isCheckoutEndpoint(path, method)){
            String key= resolveKey(request);
            Bucket bucket= checkoutCache.get(key, k -> buildCheckoutBucket());
            if(!bucket.tryConsume(1)){
                rejectRequest(response, "Too many checkout attempts. Please wait before trying again.");
                return;
            }
        } else if (isLoginEndpoint(path, method)) {
            String key= resolveKey(request);
            Bucket bucket= loginCache.get(key, k -> buildLoginBucket());
            if(!bucket.tryConsume(1)){
                rejectRequest(response, "Too many login attempts. Please wait before trying again later.");
                return;
            }
        }else if(isRegisterEndpoint(path, method)){
            String key= resolveKey(request);
            Bucket bucket= registerCache.get(key, k -> buildRegisterBucket());
            if(!bucket.tryConsume(1)){
                rejectRequest(response, "Too many registration attempts. Please try again later. ");
                return;
            }
        }
        //refresh & logout get no rate limit
        filterChain.doFilter(request, response);
    }
    //5 request per minute per user
    private Bucket buildCheckoutBucket(){
        return Bucket.builder().addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1))
                .build()).build();
    }
    //10 requests per minute per user
    private Bucket buildLoginBucket(){
        return Bucket.builder().addLimit(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1))
                .build()).build();
    }
    //3 requests per hour per user
    private Bucket buildRegisterBucket(){
        return Bucket.builder().addLimit(Bandwidth.builder().capacity(3).refillGreedy(3, Duration.ofHours(1))
                .build()).build();
    }
    //Jwt email when token present
    private String resolveKey(HttpServletRequest request){
        String authHeader= request.getHeader("Authorization");
        if(authHeader != null && authHeader.startsWith("Bearer ")){
            String token= authHeader.substring(7);
            try{
                return jwtUtil.extractEmail(token);
            } catch (Exception e) {

            }
        }
        String forwarded= request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
    private boolean isCheckoutEndpoint(String path, String method){
        return path.equals("/api/orders/checkout") && method.equals("POST");
    }
    private boolean isLoginEndpoint(String path, String method){
        return path.equals("/api/auth/login") && method.equals("POST");
    }
    private boolean isRegisterEndpoint(String path, String method){
        return path.equals("/api/auth/register") && method.equals("POST");
    }
    //matches GlobalExceptioinHandler
    private void rejectRequest(HttpServletResponse response, String message) throws IOException{
        String correlationId= UUID.randomUUID().toString();
        log.warn("Rate limit exceeded [{}] - {}", correlationId, message);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {
                  "status": 429,
                  "message": "%s",
                  "correlationId": "%s",
                  "timestamp": "%s"
                }
                """.formatted(message, correlationId, LocalDateTime.now()));
    }
}
