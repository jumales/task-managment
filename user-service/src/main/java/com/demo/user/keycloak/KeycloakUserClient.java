package com.demo.user.keycloak;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.dto.UserRequest;
import com.demo.common.exception.DuplicateResourceException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.user.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Low-level client for the Keycloak Admin REST API.
 * This is the only class in user-service that knows about the Keycloak {@code UserRepresentation}
 * JSON shape. All callers receive and work with {@link UserDto}.
 *
 * <p>User IDs exposed to callers are Keycloak's own UUIDs (the {@code sub} claim in JWTs).
 * The {@code name} field is stored in and read from Keycloak's {@code firstName} attribute;
 * {@code lastName} is not used.
 *
 * <p>Deletion is implemented as a disable ({@code enabled=false}) rather than a hard delete,
 * so that UUID references stored in other services (e.g. task assignees) remain resolvable.
 * Disabled users are excluded from list and by-username queries.
 */
@Component
public class KeycloakUserClient implements KeycloakUserPort {

    private static final String ATTR_AVATAR = "avatarFileId";
    private static final String ATTR_LANGUAGE = "language";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String ROLE_WEB_APP = "WEB_APP";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_DEVELOPER = "DEVELOPER";
    private static final String ROLE_QA = "QA";
    private static final String ROLE_DEVOPS = "DEVOPS";
    private static final String ROLE_PM = "PM";
    private static final String ROLE_SUPERVISOR = "SUPERVISOR";

    /** The set of realm roles that can be managed via the API; WEB_APP is excluded (always auto-assigned). */
    private static final Set<String> MANAGEABLE_ROLES = Set.of(
            ROLE_ADMIN, ROLE_DEVELOPER, ROLE_QA, ROLE_DEVOPS, ROLE_PM, ROLE_SUPERVISOR);

    private static final ParameterizedTypeReference<List<Map<String, Object>>> USER_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> ROLE_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public KeycloakUserClient(WebClient keycloakAdminClient) {
        this.webClient = keycloakAdminClient;
    }

    /**
     * Returns a paginated page of enabled human users that have the {@value #ROLE_WEB_APP} realm role.
     * Keycloak 24 does not expose a {@code /roles/{role}/users/count} endpoint, so all matching users
     * are fetched in a single call and paginated in-memory. This is acceptable because the total number
     * of application users is small in practice.
     * Service accounts (username prefix {@code service-account-}) and disabled users are excluded.
     */
    public PageResponse<UserDto> findAll(Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int size = pageable.getPageSize();

        // Fetch all WEB_APP role users in one call; Keycloak 24 has no /count endpoint for role users.
        List<Map<String, Object>> allReps = webClient.get()
                .uri(u -> u.path("/roles/" + ROLE_WEB_APP + "/users")
                        .queryParam("first", 0)
                        .queryParam("max", Integer.MAX_VALUE)
                        .queryParam("briefRepresentation", false)
                        .build())
                .retrieve()
                .bodyToMono(USER_LIST_TYPE)
                .block();

        List<UserDto> allUsers = allReps == null ? List.of() : allReps.stream()
                .filter(this::isHumanUser)
                .map(this::toDto)
                .toList();

        long totalElements = allUsers.size();
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        boolean isLast = (offset + size) >= totalElements;

        List<UserDto> page = allUsers.stream()
                .skip(offset)
                .limit(size)
                .toList();

        return new PageResponse<>(page, pageable.getPageNumber(), size, totalElements, totalPages, isLast);
    }

    /**
     * Returns the Keycloak user with the given UUID.
     * Unlike {@link #findAll}, this also returns disabled users so admins can view them.
     *
     * @throws ResourceNotFoundException if no user with {@code id} exists
     */
    @Cacheable(value = CacheConfig.USERS, key = "#id")
    public UserDto findById(UUID id) {
        try {
            Map<String, Object> rep = webClient.get()
                    .uri("/users/{id}", id)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (rep == null) throw new ResourceNotFoundException("User", id);
            return toDto(rep);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("User", id);
            }
            throw e;
        }
    }

    /**
     * Returns the enabled user matching the given username exactly, or empty if not found.
     */
    @Cacheable(value = CacheConfig.USERS_BY_USERNAME, key = "#username")
    public Optional<UserDto> findByUsername(String username) {
        List<Map<String, Object>> reps = webClient.get()
                .uri(u -> u.path("/users")
                        .queryParam("username", username)
                        .queryParam("exact", true)
                        .queryParam("enabled", true)
                        .queryParam("max", 1)
                        .build())
                .retrieve()
                .bodyToMono(USER_LIST_TYPE)
                .block();

        if (reps == null || reps.isEmpty()) return Optional.empty();
        return Optional.of(toDto(reps.get(0)));
    }

    /**
     * Batch-fetches users by UUID using parallel Keycloak calls.
     * Missing IDs (404) are silently dropped from the result.
     */
    public List<UserDto> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return Flux.fromIterable(ids)
                .flatMap(id -> webClient.get()
                        .uri("/users/{id}", id)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .onErrorResume(WebClientResponseException.NotFound.class, e -> reactor.core.publisher.Mono.empty()))
                .map(this::toDto)
                .collectList()
                .block();
    }

    /**
     * Creates a new user in Keycloak.
     * Keycloak returns 201 with a Location header; this method extracts the UUID and fetches
     * the full representation to return.
     *
     * @throws DuplicateResourceException if the username is already taken
     */
    @CacheEvict(value = {CacheConfig.USERS, CacheConfig.USERS_BY_USERNAME}, allEntries = true)
    public UserDto create(UserRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", request.getUsername());
        body.put("firstName", request.getName());
        body.put("email", request.getEmail());
        body.put("enabled", true);

        try {
            var response = webClient.post()
                    .uri("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            // Extract UUID from Location header: .../users/{uuid}
            String location = response.getHeaders().getFirst("Location");
            UUID createdId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
            assignWebAppRole(createdId);
            return findByIdDirect(createdId);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new DuplicateResourceException("Username already taken: " + request.getUsername());
            }
            throw e;
        }
    }

    /**
     * Updates the user's name, email, and active flag. Username is immutable and is not changed.
     */
    @CacheEvict(value = {CacheConfig.USERS, CacheConfig.USERS_BY_USERNAME}, allEntries = true)
    public UserDto update(UUID id, UserRequest request) {
        // Fetch current to preserve username (immutable) and existing attributes
        Map<String, Object> current = fetchRaw(id);

        Map<String, Object> body = new HashMap<>();
        body.put("username", current.get("username")); // username is immutable
        body.put("firstName", request.getName());
        body.put("email", request.getEmail());
        body.put("enabled", request.isActive());
        body.put("attributes", current.getOrDefault("attributes", Map.of()));

        webClient.put()
                .uri("/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();

        return findByIdDirect(id);
    }

    /**
     * Disables the user in Keycloak ({@code enabled=false}), effectively soft-deleting them.
     * Disabled users are excluded from list and by-username queries.
     */
    @CacheEvict(value = {CacheConfig.USERS, CacheConfig.USERS_BY_USERNAME}, allEntries = true)
    public void disable(UUID id) {
        Map<String, Object> current = fetchRaw(id);

        Map<String, Object> body = new HashMap<>(current);
        body.put("enabled", false);

        webClient.put()
                .uri("/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Updates a single Keycloak user attribute (e.g. {@code language} or {@code avatarFileId}).
     * Reads the full user first, merges the attribute, then writes back the whole representation.
     * Pass {@code null} as the value to remove the attribute.
     */
    @CacheEvict(value = {CacheConfig.USERS, CacheConfig.USERS_BY_USERNAME}, allEntries = true)
    public UserDto updateAttribute(UUID id, String key, String value) {
        Map<String, Object> current = fetchRaw(id);

        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = new HashMap<>(
                (Map<String, Object>) current.getOrDefault("attributes", new HashMap<>()));

        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, List.of(value));
        }

        Map<String, Object> body = new HashMap<>(current);
        body.put("attributes", attributes);

        webClient.put()
                .uri("/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();

        return findByIdDirect(id);
    }

    /**
     * Fetches the raw Keycloak representation without going through the cache.
     * Used internally before PUT calls to read current state.
     */
    private Map<String, Object> fetchRaw(UUID id) {
        try {
            return webClient.get()
                    .uri("/users/{id}", id)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("User", id);
            }
            throw e;
        }
    }

    /**
     * Returns the manageable realm roles currently held by the user, excluding {@value #ROLE_WEB_APP}.
     */
    public List<String> getUserRoles(UUID userId) {
        List<Map<String, Object>> roleMappings = webClient.get()
                .uri("/users/{userId}/role-mappings/realm", userId)
                .retrieve()
                .bodyToMono(ROLE_LIST_TYPE)
                .block();

        if (roleMappings == null) return List.of();
        return roleMappings.stream()
                .map(r -> (String) r.get("name"))
                .filter(MANAGEABLE_ROLES::contains)
                .toList();
    }

    /**
     * Replaces all manageable realm roles for the user. {@value #ROLE_WEB_APP} is always preserved.
     * Validates that all supplied role names are in {@link #MANAGEABLE_ROLES}.
     *
     * @throws IllegalArgumentException if any supplied role name is not a known manageable role
     */
    @CacheEvict(value = {CacheConfig.USERS, CacheConfig.USERS_BY_USERNAME}, allEntries = true)
    public void setUserRoles(UUID userId, List<String> roleNames) {
        for (String name : roleNames) {
            if (!MANAGEABLE_ROLES.contains(name)) {
                throw new IllegalArgumentException("Unknown or non-manageable role: " + name);
            }
        }

        Set<String> desired = new HashSet<>(roleNames);
        Set<String> current = new HashSet<>(getUserRoles(userId));

        Set<String> toAdd = new HashSet<>(desired);
        toAdd.removeAll(current);

        Set<String> toRemove = new HashSet<>(current);
        toRemove.removeAll(desired);

        if (!toAdd.isEmpty()) {
            List<Map<String, Object>> addRepresentations = toAdd.stream()
                    .map(this::fetchRoleRepresentation)
                    .toList();
            webClient.post()
                    .uri("/users/{userId}/role-mappings/realm", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(addRepresentations)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        }

        if (!toRemove.isEmpty()) {
            // Keycloak DELETE role-mappings requires full role representation objects, not just names
            List<Map<String, Object>> removeRepresentations = toRemove.stream()
                    .map(this::fetchRoleRepresentation)
                    .toList();
            webClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri("/users/{userId}/role-mappings/realm", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(removeRepresentations)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        }
    }

    /**
     * Assigns the {@value #ROLE_WEB_APP} realm role to the given user.
     * Called after every new user creation so the user can access the application.
     */
    private void assignWebAppRole(UUID userId) {
        Map<String, Object> role = fetchRoleRepresentation(ROLE_WEB_APP);
        webClient.post()
                .uri("/users/{userId}/role-mappings/realm", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(role))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Fetches the Keycloak representation for the given realm role by name.
     * Used to obtain the role's UUID for role assignment and removal calls.
     */
    private Map<String, Object> fetchRoleRepresentation(String roleName) {
        return webClient.get()
                .uri("/roles/{roleName}", roleName)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    /**
     * Fetches a user by ID bypassing the cache, used after create/update to return fresh data.
     * Includes the user's current manageable roles.
     */
    private UserDto findByIdDirect(UUID id) {
        return toDtoWithRoles(fetchRaw(id));
    }

    /**
     * Maps a raw Keycloak {@code UserRepresentation} to a {@link UserDto} without fetching roles.
     * Used by list endpoints to avoid N+1 Keycloak calls; {@code roles} field will be empty.
     */
    @SuppressWarnings("unchecked")
    UserDto toDto(Map<String, Object> rep) {
        UUID id = UUID.fromString((String) rep.get("id"));
        String name = (String) rep.get("firstName");
        String email = (String) rep.get("email");
        String username = (String) rep.get("username");
        boolean active = Boolean.TRUE.equals(rep.get("enabled"));

        Map<String, Object> attributes = (Map<String, Object>) rep.getOrDefault("attributes", Map.of());

        UUID avatarFileId = extractAttribute(attributes, ATTR_AVATAR)
                .map(UUID::fromString)
                .orElse(null);
        String language = extractAttribute(attributes, ATTR_LANGUAGE).orElse(DEFAULT_LANGUAGE);

        return new UserDto(id, name, email, username, active, avatarFileId, language, List.of());
    }

    /**
     * Maps a raw Keycloak {@code UserRepresentation} to a {@link UserDto}, also fetching the user's roles.
     * Used only by single-user lookups ({@link #findByIdDirect}) to keep the roles field populated.
     */
    private UserDto toDtoWithRoles(Map<String, Object> rep) {
        UserDto dto = toDto(rep);
        UUID id = UUID.fromString((String) rep.get("id"));
        return new UserDto(dto.getId(), dto.getName(), dto.getEmail(), dto.getUsername(),
                dto.isActive(), dto.getAvatarFileId(), dto.getLanguage(), getUserRoles(id));
    }

    /**
     * Returns true for real human users: enabled and username does not start with {@code service-account-}.
     * Keycloak always names service accounts {@code service-account-{clientId}} — this prefix is a
     * stable Keycloak convention and is the only reliable filter when fetching users by role
     * (the {@code serviceAccountClientId} field is absent from role-user listings).
     */
    private boolean isHumanUser(Map<String, Object> rep) {
        String username = (String) rep.get("username");
        return Boolean.TRUE.equals(rep.get("enabled")) && username != null && !username.startsWith("service-account-");
    }

    /** Extracts the first value of a Keycloak user attribute list, or empty if absent. */
    @SuppressWarnings("unchecked")
    private Optional<String> extractAttribute(Map<String, Object> attributes, String key) {
        Object raw = attributes.get(key);
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return Optional.ofNullable((String) list.get(0));
        }
        return Optional.empty();
    }
}
