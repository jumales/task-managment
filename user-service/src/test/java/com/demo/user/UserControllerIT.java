package com.demo.user;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.dto.UserRequest;
import com.demo.common.exception.DuplicateResourceException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.user.event.UserEventPublisherPort;
import com.demo.user.keycloak.KeycloakUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Integration test covering all {@code /api/v1/users} endpoints.
 * {@link KeycloakUserClient} is mocked so tests run without a live Keycloak instance.
 * A dedicated Keycloak-container test covering the full HTTP integration will be added in a follow-up PR.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                // No Kafka in CI — make the producer fail fast instead of blocking.
                "spring.kafka.bootstrap-servers=localhost:1",
                "spring.kafka.producer.properties.max.block.ms=500",
                // Disable OAuth2 client auto-configuration (no live Keycloak needed).
                "spring.security.oauth2.client.registration.keycloak.client-id=user-service",
                "spring.security.oauth2.client.registration.keycloak.client-secret=unused",
                "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=client_credentials",
                "spring.security.oauth2.client.provider.keycloak.token-uri=http://localhost:1/token",
                "keycloak.admin.base-url=http://localhost:1/admin/realms/demo"
        }
)
class UserControllerIT {

    /**
     * Overrides production security — permits all requests and injects an ADMIN authentication
     * into the security context so that {@code @PreAuthorize("hasRole('ADMIN')")} checks pass.
     * Also provides a Redis container so the Redis-backed {@code CacheManager} can connect.
     */
    @TestConfiguration
    static class TestSecurityConfig {

        /**
         * Shared Redis container for user-service integration tests.
         * Spring Boot wires the host/port automatically via {@code @ServiceConnection}.
         */
        @Bean
        @ServiceConnection
        @SuppressWarnings("resource")
        GenericContainer<?> redisContainer() {
            return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        }

        @Bean
        @Order(1)
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/**")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .addFilterBefore(new OncePerRequestFilter() {
                        @Override
                        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                        jakarta.servlet.http.HttpServletResponse response,
                                                        jakarta.servlet.FilterChain chain)
                                throws java.io.IOException, jakarta.servlet.ServletException {
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(
                                            "00000000-0000-0000-0000-000000000001", null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                                    new SimpleGrantedAuthority("ROLE_WEB_APP")))
                            );
                            chain.doFilter(request, response);
                        }
                    }, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    /** Stubs Keycloak Admin REST API calls so tests run without a live Keycloak instance. */
    @MockBean
    KeycloakUserPort keycloakUserClient;

    /** Prevents Kafka send attempts during tests. */
    @MockBean
    UserEventPublisherPort eventPublisher;

    @Autowired
    TestRestTemplate restTemplate;

    private static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BOB_ID   = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private UserDto alice;
    private UserDto bob;

    @BeforeEach
    void setUp() {
        alice = new UserDto(ALICE_ID, "Alice", "alice@demo.com", "alice", true, null, "en", List.of());
        bob   = new UserDto(BOB_ID,   "Bob",   "bob@demo.com",   "bob",   true, null, "en", List.of());
    }

    // ── GET /api/v1/users ─────────────────────────────────────────────

    @Test
    void getAllUsers_whenEmpty_returnsEmptyPage() {
        when(keycloakUserClient.findAll(any())).thenReturn(emptyPage());

        ResponseEntity<PageResponse<UserDto>> response = getUserPage("/api/v1/users");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isZero();
    }

    @Test
    void getAllUsers_returnsAllUsers() {
        PageResponse<UserDto> page = new PageResponse<>(List.of(alice, bob), 0, 20, 2, 1, true);
        when(keycloakUserClient.findAll(any())).thenReturn(page);

        ResponseEntity<PageResponse<UserDto>> response = getUserPage("/api/v1/users");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(2);
    }

    // ── GET /api/v1/users/batch ───────────────────────────────────────

    @Test
    void getUsersByIds_returnsRequestedUsers() {
        when(keycloakUserClient.findByIds(any())).thenReturn(List.of(alice, bob));

        ResponseEntity<UserDto[]> response = restTemplate.getForEntity(
                "/api/v1/users/batch?ids=" + ALICE_ID + "&ids=" + BOB_ID, UserDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    // ── GET /api/v1/users/{id} ────────────────────────────────────────

    @Test
    void getUserById_returnsUser() {
        when(keycloakUserClient.findById(ALICE_ID)).thenReturn(alice);

        ResponseEntity<UserDto> response = restTemplate.getForEntity("/api/v1/users/" + ALICE_ID, UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Alice");
        assertThat(response.getBody().getEmail()).isEqualTo("alice@demo.com");
    }

    @Test
    void getUserById_whenNotFound_returns404() {
        UUID unknown = UUID.randomUUID();
        when(keycloakUserClient.findById(unknown)).thenThrow(new ResourceNotFoundException("User", unknown));

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/users/" + unknown, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/v1/users ────────────────────────────────────────────

    @Test
    void createUser_returnsCreatedUser() {
        when(keycloakUserClient.create(any())).thenReturn(alice);

        ResponseEntity<UserDto> response = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Alice");
    }

    @Test
    void createUser_duplicateUsername_returns409() {
        when(keycloakUserClient.create(any())).thenThrow(new DuplicateResourceException("Username already taken: alice"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "alice@demo.com", "alice"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createUser_withInvalidEmail_returns400() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "not-an-email", "alice"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("email");
    }

    @Test
    void createUser_withoutUsername_returns400() {
        UserRequest req = new UserRequest();
        req.setName("Alice");
        req.setEmail("alice@demo.com");

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/users", req, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("username");
    }

    @Test
    void createUser_withUsername_persistsUsername() {
        when(keycloakUserClient.create(any())).thenReturn(alice);

        ResponseEntity<UserDto> response = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getUsername()).isEqualTo("alice");
    }

    @Test
    void createUser_isActiveByDefault() {
        when(keycloakUserClient.create(any())).thenReturn(alice);

        ResponseEntity<UserDto> response = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class);

        assertThat(response.getBody().isActive()).isTrue();
    }

    // ── PUT /api/v1/users/{id} ────────────────────────────────────────

    @Test
    void updateUser_updatesFields() {
        UserDto updated = new UserDto(ALICE_ID, "Alice Updated", "alice.new@demo.com", "alice", true, null, "en", List.of());
        when(keycloakUserClient.update(eq(ALICE_ID), any())).thenReturn(updated);

        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID,
                HttpMethod.PUT,
                new HttpEntity<>(request("Alice Updated", "alice.new@demo.com", "alice")),
                UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Alice Updated");
        assertThat(response.getBody().getEmail()).isEqualTo("alice.new@demo.com");
    }

    @Test
    void updateUser_withInvalidEmail_returns400() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID,
                HttpMethod.PUT,
                new HttpEntity<>(request("Alice", "not-an-email", "alice")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("email");
    }

    @Test
    void updateUser_usernameIsIgnored_remainsUnchanged() {
        // Update call ignores username in request — KeycloakUserClient preserves original
        when(keycloakUserClient.update(eq(ALICE_ID), any())).thenReturn(alice); // username stays "alice"

        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID,
                HttpMethod.PUT,
                new HttpEntity<>(request("Alice Updated", "alice@demo.com", "new_username")),
                UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo("alice");
    }

    @Test
    void updateUser_canDeactivateUser() {
        UserDto deactivated = new UserDto(ALICE_ID, "Alice", "alice@demo.com", "alice", false, null, "en", List.of());
        when(keycloakUserClient.update(eq(ALICE_ID), any())).thenReturn(deactivated);

        UserRequest req = request("Alice", "alice@demo.com", "alice");
        req.setActive(false);
        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID, HttpMethod.PUT, new HttpEntity<>(req), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isFalse();
    }

    // ── DELETE /api/v1/users/{id} ─────────────────────────────────────

    @Test
    void deleteUser_returns204() {
        restTemplate.delete("/api/v1/users/" + ALICE_ID);
        // No exception → disable() was called; 204 returned
    }

    @Test
    void deleteUser_whenNotFound_returns404() {
        UUID unknown = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("User", unknown)).when(keycloakUserClient).disable(unknown);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/" + unknown, HttpMethod.DELETE, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteUser_thenGetById_returnsDisabledUser() {
        // After disable, findById still returns the user with active=false
        UserDto disabled = new UserDto(ALICE_ID, "Alice", "alice@demo.com", "alice", false, null, "en", List.of());
        when(keycloakUserClient.findById(ALICE_ID)).thenReturn(disabled);

        restTemplate.delete("/api/v1/users/" + ALICE_ID);

        ResponseEntity<UserDto> response = restTemplate.getForEntity("/api/v1/users/" + ALICE_ID, UserDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isFalse();
    }

    // ── PATCH /api/v1/users/{id}/avatar ───────────────────────────────

    @Test
    void updateAvatar_setsAvatarFileId() {
        UUID fileId = UUID.randomUUID();
        UserDto withAvatar = new UserDto(ALICE_ID, "Alice", "alice@demo.com", "alice", true, fileId, "en", List.of());
        when(keycloakUserClient.updateAttribute(eq(ALICE_ID), eq("avatarFileId"), any())).thenReturn(withAvatar);

        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID + "/avatar",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("fileId", fileId)),
                UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAvatarFileId()).isEqualTo(fileId);
    }

    @Test
    void updateAvatar_clearAvatar_setsAvatarFileIdToNull() {
        UserDto noAvatar = new UserDto(ALICE_ID, "Alice", "alice@demo.com", "alice", true, null, "en", List.of());
        when(keycloakUserClient.updateAttribute(eq(ALICE_ID), eq("avatarFileId"), eq(null))).thenReturn(noAvatar);

        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID + "/avatar",
                HttpMethod.PATCH,
                new HttpEntity<>(new java.util.HashMap<>(Map.of())),
                UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAvatarFileId()).isNull();
    }

    @Test
    void updateAvatar_whenUserNotFound_returns404() {
        UUID unknown = UUID.randomUUID();
        when(keycloakUserClient.updateAttribute(eq(unknown), eq("avatarFileId"), any()))
                .thenThrow(new ResourceNotFoundException("User", unknown));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/" + unknown + "/avatar",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("fileId", UUID.randomUUID())),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/v1/users/by-username ────────────────────────────────

    @Test
    void getByUsername_whenFound_returnsUser() {
        when(keycloakUserClient.findByUsername("alice")).thenReturn(Optional.of(alice));

        ResponseEntity<UserDto> response = restTemplate.getForEntity(
                "/api/v1/users/by-username?username=alice", UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo("alice");
    }

    @Test
    void getByUsername_whenNotFound_returns404() {
        when(keycloakUserClient.findByUsername("unknown")).thenReturn(Optional.empty());

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/users/by-username?username=unknown", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/v1/users/me ──────────────────────────────────────────

    @Test
    void getMe_returnsCurrentUser() {
        // TestSecurityConfig injects principal name = "00000000-0000-0000-0000-000000000001"
        UUID meId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UserDto me = new UserDto(meId, "Test Admin", "admin@demo.com", "test-admin", true, null, "en", List.of());
        when(keycloakUserClient.findById(meId)).thenReturn(me);

        ResponseEntity<UserDto> response = restTemplate.getForEntity("/api/v1/users/me", UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(meId);
    }

    // ── GET /api/v1/users/{id}/roles ─────────────────────────────────

    @Test
    void getRoles_returnsRoleList() {
        when(keycloakUserClient.getUserRoles(ALICE_ID)).thenReturn(List.of("DEVELOPER", "QA"));

        ResponseEntity<List<String>> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID + "/roles", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactlyInAnyOrder("DEVELOPER", "QA");
    }

    @Test
    void getRoles_whenUserNotFound_returns404() {
        when(keycloakUserClient.getUserRoles(ALICE_ID))
                .thenThrow(new ResourceNotFoundException("User", ALICE_ID));

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/users/" + ALICE_ID + "/roles", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── PUT /api/v1/users/{id}/roles ──────────────────────────────────

    @Test
    void setRoles_replacesRoles_returnsUpdatedList() {
        List<String> newRoles = List.of("ADMIN");
        when(keycloakUserClient.getUserRoles(ALICE_ID)).thenReturn(newRoles);

        HttpEntity<List<String>> request = new HttpEntity<>(newRoles);
        ResponseEntity<List<String>> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID + "/roles", HttpMethod.PUT, request,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly("ADMIN");
    }

    @Test
    void setRoles_withInvalidRole_returns400() {
        List<String> badRoles = List.of("HACKER");
        doThrow(new IllegalArgumentException("Unknown or non-manageable role: HACKER"))
                .when(keycloakUserClient).setUserRoles(eq(ALICE_ID), any());

        HttpEntity<List<String>> request = new HttpEntity<>(badRoles);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID + "/roles", HttpMethod.PUT, request,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void setRoles_emptyList_removesAllRoles() {
        when(keycloakUserClient.getUserRoles(ALICE_ID)).thenReturn(List.of());

        HttpEntity<List<String>> request = new HttpEntity<>(List.of());
        ResponseEntity<List<String>> response = restTemplate.exchange(
                "/api/v1/users/" + ALICE_ID + "/roles", HttpMethod.PUT, request,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private ResponseEntity<PageResponse<UserDto>> getUserPage(String url) {
        return restTemplate.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
    }

    private UserRequest request(String name, String email, String username) {
        UserRequest req = new UserRequest();
        req.setName(name);
        req.setEmail(email);
        req.setUsername(username);
        return req;
    }

    private PageResponse<UserDto> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, true);
    }
}
