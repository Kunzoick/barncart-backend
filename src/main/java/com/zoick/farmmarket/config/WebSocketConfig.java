package com.zoick.farmmarket.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
//configures STOMP over Websocket. Enables the message broker for topic broadcasts and user queues
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{
    @Value("${websocket.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry){
        /**
         * enables simple in-memory broker for these destinations
         * topic(broadcast to all subscribes), queue(private user channel)
         */
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");//prefix for message destinations
        registry.setUserDestinationPrefix("/user");
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins).withSockJS();
    }
}
