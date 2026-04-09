package com.demo.user.service;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.dto.UserRequest;
import com.demo.user.event.UserEventPublisherPort;
import com.demo.user.keycloak.KeycloakUserPort;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for user management. Delegates all data access to {@link KeycloakUserClient}
 * and publishes lifecycle events via {@link KafkaUserEventPublisher}.
 *
 * <p>User IDs are Keycloak's own UUIDs, identical to the {@code sub} claim in JWTs.
 * There is no backing database — Keycloak is the single source of truth.
 */
@Service
public class UserService {

    private final KeycloakUserPort keycloakUserClient;
    private final UserEventPublisherPort eventPublisher;

    public UserService(KeycloakUserPort keycloakUserClient,
                       UserEventPublisherPort eventPublisher) {
        this.keycloakUserClient = keycloakUserClient;
        this.eventPublisher = eventPublisher;
    }

    /** Returns a paginated page of enabled users. */
    public PageResponse<UserDto> findAll(Pageable pageable) {
        return keycloakUserClient.findAll(pageable);
    }

    /** Returns the user with the given Keycloak UUID; also returns disabled users for admin visibility. */
    public UserDto findById(UUID id) {
        return keycloakUserClient.findById(id);
    }

    /** Returns the enabled user with the given username, or empty if not found. */
    public Optional<UserDto> findByUsername(String username) {
        return keycloakUserClient.findByUsername(username);
    }

    /** Returns users whose Keycloak UUIDs are in the given collection. */
    public List<UserDto> findByIds(Collection<UUID> ids) {
        return keycloakUserClient.findByIds(ids);
    }

    /**
     * Creates a new user in Keycloak and publishes a CREATED event.
     *
     * @throws com.demo.common.exception.DuplicateResourceException if the username is already taken
     */
    public UserDto create(UserRequest request) {
        UserDto created = keycloakUserClient.create(request);
        eventPublisher.publishCreated(created);
        return created;
    }

    /**
     * Updates the user's name, email, and active flag; username is immutable.
     * Publishes an UPDATED event.
     */
    public UserDto update(UUID id, UserRequest request) {
        UserDto updated = keycloakUserClient.update(id, request);
        eventPublisher.publishUpdated(updated);
        return updated;
    }

    /**
     * Sets the user's preferred UI language.
     *
     * @param language ISO 639-1 code, e.g. "en" or "hr"
     */
    public UserDto updateLanguage(UUID userId, String language) {
        return keycloakUserClient.updateAttribute(userId, "language", language);
    }

    /**
     * Sets the user's avatar to the file identified by {@code fileId}.
     * Pass {@code null} to remove the avatar.
     */
    public UserDto updateAvatar(UUID userId, UUID fileId) {
        String value = fileId == null ? null : fileId.toString();
        return keycloakUserClient.updateAttribute(userId, "avatarFileId", value);
    }

    /** Returns the manageable realm roles currently held by the given user, excluding WEB_APP. */
    public List<String> getUserRoles(UUID userId) {
        return keycloakUserClient.getUserRoles(userId);
    }

    /**
     * Replaces all manageable realm roles for the user; WEB_APP is always preserved.
     *
     * @throws IllegalArgumentException if any supplied role name is not a known manageable role
     */
    public void setUserRoles(UUID userId, List<String> roleNames) {
        keycloakUserClient.setUserRoles(userId, roleNames);
    }

    /**
     * Disables the user in Keycloak ({@code enabled=false}), effectively a soft-delete.
     * Publishes a DELETED event.
     */
    public void delete(UUID id) {
        keycloakUserClient.disable(id);
        eventPublisher.publishDeleted(id);
    }
}
