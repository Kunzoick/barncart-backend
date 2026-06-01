package com.zoick.farmmarket.config;
import com.zoick.farmmarket.domain.auth.JwtAuthFilter;
import com.zoick.farmmarket.domain.auth.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.zoick.farmmarket.domain.auth.CustomUserDetailsService;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
/*
The central Spring security configuration. Defines which routes are public,
which require authentication, and which require ADMIN role. Registers the JWT filter before Spring's defaukt username/password filter
cors configured globally-> allowed origin driven by Websoket_allowed_origins
security headers configured explicitly(hsts, csp, referrer policy, permission policy)
 */
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    @Value("${websocket.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable).sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll() //public auth endpoints
                .requestMatchers(HttpMethod.GET, "/api/listings/**").permitAll()//public listing endpoints-> anyone can browse
                        .requestMatchers(HttpMethod.GET, "/api/produce/**").permitAll()
                .requestMatchers("/api/webhooks/stripe").permitAll()//Stripe webhook-> must be public,stripe has no JWT
                        .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")// admin only
                .anyRequest().authenticated())// everything requires authentication
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class).headers(headers -> headers
                        //already set by spring security-> x-content-type-options(nosniff), x-frame-options, cache-control)
                        //hsts- force https, browsers remember for max-age duration(uses 1 day cos of development) will increase to 31536000(1yr) for production
                                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(false).maxAgeInSeconds(86400))
                        //CSP-> controls what the browser is allowed to load, default-src only resources from your own origin
                                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; "+
                                        "connect-src 'self' ws://localhost:5173 http://localhost:5173 ws://localhost:8080 http://localhost:8080; " +
                                        "frame-ancestors 'none'; " +
                                        "form-action 'self'"))
                        //referrer-Policy-> no referrer information sent cross-origin & prevents leaking API paths or user data in referrer headers
                                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        //permissions-policy-> disables browser features this app does not use
                                .permissionsPolicyHeader(permissions -> permissions.policy("camera(), microphone=(), geolocation=(self), "+
                                        "payment=(), usb=(), fullscreen=(self)")));
        return http.build();
    }
    //CORS configuration applied to all endpoints globally
    @Bean
    public CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration config= new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigins));
        //cross-origin request with Authorizatiion or content-type headers
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        //authorization must be listed or jwt tokens are blocked by the browser
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        //no custom response headers need to be readable by the frontend yet
        config.setExposedHeaders(List.of());
        //false-> localStorage used for refresh token
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source= new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    //instantiated here so spring does not auto-register
    @Bean
    public RateLimitFilter rateLimitFilter(){
        return new RateLimitFilter(jwtUtil);
    }
    //explicit ordering at HIGHEST_PRECEDENCE
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterFilterRegistration(RateLimitFilter filter){
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

