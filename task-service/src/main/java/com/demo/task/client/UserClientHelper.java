package com.demo.task.client;

import com.demo.common.dto.UserDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resilient wrapper around {@link UserClient} that handles user-service unavailability
 * gracefully by returning safe fallback values instead of propagating exceptions.
 * Consolidates the duplicate resilience patterns previously spread across
 * {@code TaskService}, {@code TaskWorkLogService}, and {@code TaskParticipantService}.
 */
@Component
public class UserClientHelper {

    private final UserClient userClient;

    public UserClientHelper(UserClient userClient) {
        this.userClient = userClient;
    }

    /**
     * Returns the display name of a single user, or {@code null} if the user is not found
     * or user-service is unavailable.
     */
    public String resolveUserName(UUID userId) {
        if (userId == null) return null;
        try {
            return userClient.getUserById(userId).getName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Batch-fetches user display names keyed by UUID.
     * Returns an empty map if user-service is unavailable.
     */
    public Map<UUID, String> fetchUserNames(Set<UUID> userIds) {
        try {
            return userClient.getUsersByIds(new ArrayList<>(userIds)).stream()
                    .collect(Collectors.toMap(UserDto::getId, UserDto::getName));
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Batch-fetches full {@link UserDto} objects keyed by UUID.
     * Returns an empty map if user-service is unavailable.
     */
    public Map<UUID, UserDto> fetchUsers(Set<UUID> userIds) {
        try {
            return userClient.getUsersByIds(new ArrayList<>(userIds)).stream()
                    .collect(Collectors.toMap(UserDto::getId, u -> u));
        } catch (Exception e) {
            return Map.of();
        }
    }
}
