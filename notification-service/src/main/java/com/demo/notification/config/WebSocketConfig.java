package com.demo.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures a STOMP-over-WebSocket endpoint for real-time task push notifications.
 * Clients connect to {@code /ws/tasks} (with SockJS fallback), subscribe to
 * {@code /topic/tasks/{taskId}}, and receive a push whenever any tracked field on
 * that task changes.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins:*}")
    private String allowedOriginsConfig;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker; clients subscribe under /topic
        registry.enableSimpleBroker("/topic");
        // Prefix for messages routed to @MessageMapping methods (none yet)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOriginsConfig.split(",");
        registry.addEndpoint("/ws/tasks")
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }
}
