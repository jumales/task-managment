package com.demo.notification.client;

import com.demo.common.dto.UserDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resilient wrapper around {@link UserClient} that applies a circuit breaker to guard
 * against user-service unavailability. Returns {@code null} when the circuit is open
 * so the caller can skip the notification gracefully instead of propagating the failure.
 */
@Component
public class UserClientHelper {

    private static final Logger log = LoggerFactory.getLogger(UserClientHelper.class);

    private final UserClient userClient;

    public UserClientHelper(UserClient userClient) {
        this.userClient = userClient;
    }

    /**
     * Returns the user by ID, or {@code null} if user-service is unavailable or the circuit is open.
     */
    @CircuitBreaker(name = "userService", fallbackMethod = "getUserByIdFallback")
    public UserDto getUserById(UUID id) {
        return userClient.getUserById(id);
    }

    private UserDto getUserByIdFallback(UUID id, Throwable t) {
        log.warn("user-service circuit open — cannot resolve user {} for notification: {}", id, t.getMessage());
        return null;
    }
}
