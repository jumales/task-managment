package com.demo.task.client;

import com.demo.common.dto.UserDto;
import com.demo.task.config.CacheConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resilient wrapper around {@link UserClient} that applies a circuit breaker to guard
 * against user-service unavailability. Returns safe fallback values (null / empty maps)
 * when the circuit is open, so task operations degrade gracefully instead of failing.
 */
@Component
public class UserClientHelper {

    private static final Logger log = LoggerFactory.getLogger(UserClientHelper.class);

    private final UserClient userClient;

    public UserClientHelper(UserClient userClient) {
        this.userClient = userClient;
    }

    /**
     * Resolves the user-service UUID for the given Keycloak preferred_username.
     * Cached to avoid repeated remote calls on every task creation.
     * Returns {@code null} if the username is not found or user-service is unavailable.
     */
    @Cacheable(value = CacheConfig.USER_NAMES, key = "'username:' + #username", unless = "#result == null")
    @CircuitBreaker(name = "userService", fallbackMethod = "resolveUserIdByUsernameFallback")
    public UUID resolveUserIdByUsername(String username) {
        if (username == null) return null;
        return userClient.getUserByUsername(username).getId();
    }

    private UUID resolveUserIdByUsernameFallback(String username, Throwable t) {
        log.warn("user-service circuit open — cannot resolve user ID for username {}: {}", username, t.getMessage());
        return null;
    }

    /**
     * Returns the display name of a single user, or {@code null} if the user is not found
     * or user-service is unavailable. Result is cached to avoid repeated remote calls.
     */
    @Cacheable(value = CacheConfig.USER_NAMES, key = "#userId", unless = "#result == null")
    @CircuitBreaker(name = "userService", fallbackMethod = "resolveUserNameFallback")
    public String resolveUserName(UUID userId) {
        if (userId == null) return null;
        return userClient.getUserById(userId).getName();
    }

    private String resolveUserNameFallback(UUID userId, Throwable t) {
        log.warn("user-service circuit open — cannot resolve name for user {}: {}", userId, t.getMessage());
        return null;
    }

    /**
     * Batch-fetches user display names keyed by UUID.
     * Returns an empty map if user-service is unavailable.
     */
    @CircuitBreaker(name = "userService", fallbackMethod = "fetchUserNamesFallback")
    public Map<UUID, String> fetchUserNames(Set<UUID> userIds) {
        return userClient.getUsersByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(UserDto::getId, UserDto::getName));
    }

    private Map<UUID, String> fetchUserNamesFallback(Set<UUID> userIds, Throwable t) {
        log.warn("user-service circuit open — cannot fetch user names for {} ids: {}", userIds.size(), t.getMessage());
        return Map.of();
    }

    /**
     * Batch-fetches full {@link UserDto} objects keyed by UUID.
     * Returns an empty map if user-service is unavailable.
     */
    @CircuitBreaker(name = "userService", fallbackMethod = "fetchUsersFallback")
    public Map<UUID, UserDto> fetchUsers(Set<UUID> userIds) {
        return userClient.getUsersByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(UserDto::getId, u -> u));
    }

    private Map<UUID, UserDto> fetchUsersFallback(Set<UUID> userIds, Throwable t) {
        log.warn("user-service circuit open — cannot fetch users for {} ids: {}", userIds.size(), t.getMessage());
        return Map.of();
    }
}
