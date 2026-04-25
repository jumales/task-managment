package com.demo.notification.service;

import com.demo.common.dto.DeviceTokenRequest;
import com.demo.common.dto.DeviceTokenResponse;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.notification.model.DeviceToken;
import com.demo.notification.repository.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for registering, rotating, and removing device push tokens.
 * A (userId, token) pair is unique while active; soft-deleted rows can be revived.
 */
@Service
public class DeviceTokenService {

    private final DeviceTokenRepository repository;

    public DeviceTokenService(DeviceTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Registers a new token for the user, or revives a previously soft-deleted row with the same (userId, token).
     * Updates {@code lastSeenAt} and {@code appVersion} on revival.
     */
    @Transactional
    public DeviceTokenResponse register(UUID userId, DeviceTokenRequest request) {
        // Revival path: token existed and was soft-deleted — bring it back.
        return repository.findByTokenAndDeletedAtIsNull(request.getToken())
                .filter(t -> t.getUserId().equals(userId))
                .map(existing -> {
                    existing.setLastSeenAt(Instant.now());
                    existing.setAppVersion(request.getAppVersion());
                    return toResponse(repository.save(existing));
                })
                .orElseGet(() -> {
                    // Revive soft-deleted or create fresh.
                    DeviceToken token = DeviceToken.builder()
                            .userId(userId)
                            .token(request.getToken())
                            .platform(request.getPlatform())
                            .appVersion(request.getAppVersion())
                            .createdAt(Instant.now())
                            .lastSeenAt(Instant.now())
                            .build();
                    return toResponse(repository.save(token));
                });
    }

    /**
     * Rotates a token: soft-deletes the old token and registers the new one.
     * No-op on the delete side if the old token is already inactive.
     */
    @Transactional
    public DeviceTokenResponse rotate(UUID userId, String oldToken, DeviceTokenRequest request) {
        repository.findByTokenAndDeletedAtIsNull(oldToken)
                .filter(t -> t.getUserId().equals(userId))
                .ifPresent(t -> {
                    t.setDeletedAt(Instant.now());
                    repository.save(t);
                });
        return register(userId, request);
    }

    /**
     * Soft-deletes the specified token for the authenticated user.
     * Throws {@link ResourceNotFoundException} if the token is not found or belongs to a different user.
     */
    @Transactional
    public void delete(UUID userId, String token) {
        DeviceToken deviceToken = repository.findByTokenAndDeletedAtIsNull(token)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("DeviceToken", token));
        deviceToken.setDeletedAt(Instant.now());
        repository.save(deviceToken);
    }

    /** Returns all active tokens for the authenticated user. */
    public List<DeviceTokenResponse> listForUser(UUID userId) {
        return repository.findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Soft-deletes a token by its string value, called internally when FCM reports it as invalid. */
    @Transactional
    public void softDeleteByToken(String token) {
        repository.findByTokenAndDeletedAtIsNull(token).ifPresent(t -> {
            t.setDeletedAt(Instant.now());
            repository.save(t);
        });
    }

    private DeviceTokenResponse toResponse(DeviceToken t) {
        return new DeviceTokenResponse(
                t.getId(), t.getUserId(), t.getToken(), t.getPlatform(),
                t.getAppVersion(), t.getCreatedAt(), t.getLastSeenAt());
    }
}
