package com.demo.notification.repository;

import com.demo.notification.model.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for device push tokens. All queries exclude soft-deleted rows. */
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    /** Returns all active tokens for a user. */
    List<DeviceToken> findByUserIdAndDeletedAtIsNull(UUID userId);

    /** Returns an active token by its token string. */
    Optional<DeviceToken> findByTokenAndDeletedAtIsNull(String token);

    /** Checks whether an active (userId, token) pair already exists — used for uniqueness validation. */
    boolean existsByUserIdAndTokenAndDeletedAtIsNull(UUID userId, String token);
}
