package com.zoick.farmmarket.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
//without this any authenticated user can subscribe to another users private order channel by guessing their UUID
public class WebSocketSecurityConfig {
    //disables websocket csrf to match the stateless jwt http layer
    //if crsf is not disabled here, browser stomp connections fail because the connect frame does not carry a csrf token
    @Bean
    public static boolean sameOriginDisabled(){
        return true;
    }
    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages){
        messages.simpSubscribeDestMatchers("/topic/listing/*/inventory").permitAll()//inventory public
                .simpSubscribeDestMatchers("/user/queue/orders").authenticated()//private order channel(auth only)
                .anyMessage().authenticated();//all other messages require authentication
        return messages.build();
    }
}
