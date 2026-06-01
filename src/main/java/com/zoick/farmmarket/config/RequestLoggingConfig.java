package com.zoick.farmmarket.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
// Configures request logging for all incoming HTTP requests.
// Logs method, URI, client IP, and query string.
// Payload logging disabled — request bodies contain passwords and payment data.
public class RequestLoggingConfig {
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter(){
        CommonsRequestLoggingFilter filter= new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);//logs client IP
        filter.setIncludeQueryString(true);//logs query string
        filter.setIncludePayload(false);//logs request body
        filter.setIncludeHeaders(false);//never log headers
        return filter;
    }
}
