package com.demo.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * Configures STOMP WebSocket security by validating JWT tokens on CONNECT frames.
 * Active only when Spring WebSocket is on the classpath.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer")
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public WebSocketSecurityConfig(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor());
    }

    /**
     * Intercepts STOMP CONNECT frames and validates the JWT from the Authorization header.
     * Throws {@link JwtException} if the token is missing, malformed, or invalid.
     */
    @Bean
    public ChannelInterceptor stompAuthInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
                    return message;
                }

                List<String> authHeaders = accessor.getNativeHeader(AUTHORIZATION_HEADER);
                if (authHeaders == null || authHeaders.isEmpty()) {
                    throw new JwtException("Missing Authorization header on STOMP CONNECT");
                }

                String bearer = authHeaders.get(0);
                if (!bearer.startsWith(BEARER_PREFIX)) {
                    throw new JwtException("Authorization header must start with 'Bearer '");
                }

                String token = bearer.substring(BEARER_PREFIX.length());
                var jwt = jwtDecoder.decode(token); // throws JwtException on invalid token
                accessor.setUser(new JwtAuthenticationToken(jwt));

                return message;
            }
        };
    }
}
