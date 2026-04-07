package com.demo.user.keycloak;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.dto.UserRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for user data access. Implemented by {@link KeycloakUserClient} which delegates to the
 * Keycloak Admin REST API. Extracted as an interface so it can be substituted in tests.
 */
public interface KeycloakUserPort {

    /** Returns a paginated page of enabled users. */
    PageResponse<UserDto> findAll(Pageable pageable);

    /** Returns the user with the given UUID (includes disabled users for admin visibility). */
    UserDto findById(UUID id);

    /** Returns the enabled user matching the given username exactly, or empty if not found. */
    Optional<UserDto> findByUsername(String username);

    /** Batch-fetches users by UUID; missing IDs are silently dropped. */
    List<UserDto> findByIds(Collection<UUID> ids);

    /** Creates a new user; throws {@link com.demo.common.exception.DuplicateResourceException} if username is taken. */
    UserDto create(UserRequest request);

    /** Updates name, email, and active flag; username is immutable. */
    UserDto update(UUID id, UserRequest request);

    /** Disables the user (soft-delete). */
    void disable(UUID id);

    /** Updates a single Keycloak user attribute; pass {@code null} value to remove it. */
    UserDto updateAttribute(UUID id, String key, String value);
}
