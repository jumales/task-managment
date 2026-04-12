package com.demo.task.client;

import com.demo.common.dto.UserDto;
import com.demo.task.config.CacheConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
     * Resolves the authenticated user's Keycloak UUID from the Spring Security {@link Authentication}.
     * For JWT tokens, reads the {@code sub} claim directly — it IS the Keycloak user UUID,
     * so no user-service call is required.
     * For non-JWT authentication (e.g. integration tests), attempts to parse the principal name as a UUID.
     * Returns {@code null} if resolution fails.
     */
    public UUID resolveUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            // sub claim == Keycloak UUID (same value stored as assigned_user_id in task DB)
            String sub = jwtAuth.getToken().getSubject();
            try {
                return UUID.fromString(sub);
            } catch (IllegalArgumentException | NullPointerException e) {
                return null;
            }
        }
        // Non-JWT path: principal name is already a UUID string (used in integration tests)
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /**
     * Resolves the user-service UUID for the given Keycloak preferred_username.
     *
     * @deprecated No longer called from within this service — Keycloak UUIDs are resolved
     *             directly from the JWT {@code sub} claim via {@link #resolveUserId}.
     *             Kept for backwards-compatible tooling or manual Postman lookups.
     */
    @Deprecated
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
     * Falls back to email, then username, if the user has no display name set.
     */
    @Cacheable(value = CacheConfig.USER_NAMES, key = "#userId", unless = "#result == null")
    @CircuitBreaker(name = "userService", fallbackMethod = "resolveUserNameFallback")
    public String resolveUserName(UUID userId) {
        if (userId == null) return null;
        return displayName(userClient.getUserById(userId));
    }

    private String resolveUserNameFallback(UUID userId, Throwable t) {
        log.warn("user-service circuit open — cannot resolve name for user {}: {}", userId, t.getMessage());
        return null;
    }

    /**
     * Batch-fetches user display names keyed by UUID.
     * Returns an empty map if user-service is unavailable.
     * Falls back to email, then username, if a user has no display name set.
     */
    @CircuitBreaker(name = "userService", fallbackMethod = "fetchUserNamesFallback")
    public Map<UUID, String> fetchUserNames(Set<UUID> userIds) {
        return userClient.getUsersByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(UserDto::getId, this::displayName));
    }

    private Map<UUID, String> fetchUserNamesFallback(Set<UUID> userIds, Throwable t) {
        log.warn("user-service circuit open — cannot fetch user names for {} ids: {}", userIds.size(), t.getMessage());
        return Map.of();
    }

    /**
     * Fetches the full {@link UserDto} for a single user.
     * Result is cached to avoid repeated remote calls when the same users are referenced
     * across many concurrent task operations. Returns {@code null} if the user is not
     * found or user-service is unavailable (circuit open).
     */
    @Cacheable(value = CacheConfig.USER_DTOS, key = "#userId", unless = "#result == null")
    @CircuitBreaker(name = "userService", fallbackMethod = "fetchUserFallback")
    public UserDto fetchUser(UUID userId) {
        if (userId == null) return null;
        return userClient.getUserById(userId);
    }

    private UserDto fetchUserFallback(UUID userId, Throwable t) {
        log.warn("user-service circuit open — cannot fetch user {}: {}", userId, t.getMessage());
        return null;
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

    /**
     * Returns the best available display label for a user: name → email → username.
     * Avoids null values that would cause NPE in {@link Collectors#toMap} and
     * ensures something human-readable is always shown instead of a raw UUID.
     */
    private String displayName(UserDto user) {
        if (user.getName() != null) return user.getName();
        if (user.getEmail() != null) return user.getEmail();
        return user.getUsername();
    }
}
