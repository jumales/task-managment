package com.demo.user;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.dto.UserRequest;
import com.demo.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                // No Kafka in CI for user-service — make the producer fail fast instead of
                // blocking for the default max.block.ms=60000 ms on every send() call.
                "spring.kafka.bootstrap-servers=localhost:1",
                "spring.kafka.producer.properties.max.block.ms=500"
        }
)
class UserControllerIT {

    /**
     * Overrides production security — permits all requests and injects an ADMIN authentication
     * into the security context so that @PreAuthorize("hasRole('ADMIN')") checks pass.
     */
    @TestConfiguration
    static class TestSecurityConfig {
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
                                    new UsernamePasswordAuthenticationToken("test-admin", null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            );
                            chain.doFilter(request, response);
                        }
                    }, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    // ── GET /api/v1/users ────────────────────────────────────────────

    @Test
    void getAllUsers_whenEmpty_returnsEmptyPage() {
        ResponseEntity<PageResponse<UserDto>> response = getUserPage("/api/v1/users");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isZero();
    }

    @Test
    void getAllUsers_returnsAllPersistedUsers() {
        restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class);
        restTemplate.postForEntity("/api/v1/users", request("Bob",   "bob@demo.com",   "bob"),   UserDto.class);

        ResponseEntity<PageResponse<UserDto>> response = getUserPage("/api/v1/users");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(2);
    }

    // ── GET /api/v1/users/batch ──────────────────────────────────────

    @Test
    void getUsersByIds_returnsRequestedUsers() {
        UserDto alice = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();
        UserDto bob   = restTemplate.postForEntity("/api/v1/users", request("Bob",   "bob@demo.com",   "bob"),   UserDto.class).getBody();

        ResponseEntity<UserDto[]> response = restTemplate.getForEntity(
                "/api/v1/users/batch?ids=" + alice.getId() + "&ids=" + bob.getId(), UserDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }


    // ── GET /api/v1/users/{id} ───────────────────────────────────────

    @Test
    void getUserById_returnsUser() {
        UserDto created = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();

        ResponseEntity<UserDto> response = restTemplate.getForEntity("/api/v1/users/" + created.getId(), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Alice");
        assertThat(response.getBody().getEmail()).isEqualTo("alice@demo.com");
    }

    @Test
    void getUserById_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/users/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/v1/users ───────────────────────────────────────────

    @Test
    void createUser_persistsAndReturnsUser() {
        ResponseEntity<UserDto> response = restTemplate.postForEntity("/api/v1/users", request("Carol", "carol@demo.com", "carol"), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Carol");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void createMultipleUsers_allArePersisted() {
        restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class);
        restTemplate.postForEntity("/api/v1/users", request("Bob",   "bob@demo.com",   "bob"),   UserDto.class);

        assertThat(repository.count()).isEqualTo(2);
    }

    // ── PUT /api/v1/users/{id} ───────────────────────────────────────

    @Test
    void updateUser_updatesAllFields() {
        UserDto created = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();

        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request("Alice Updated", "alice.new@demo.com", "alice")),
                UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Alice Updated");
        assertThat(response.getBody().getEmail()).isEqualTo("alice.new@demo.com");
    }

    // ── DELETE /api/v1/users/{id} ────────────────────────────────────

    @Test
    void deleteUser_removesUserFromDatabase() {
        UserDto created = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();

        restTemplate.delete("/api/v1/users/" + created.getId());

        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void deleteUser_thenGetById_returns404() {
        UserDto created = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();

        restTemplate.delete("/api/v1/users/" + created.getId());

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/users/" + created.getId(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── email validation ─────────────────────────────────────────

    @Test
    void createUser_withInvalidEmail_returns400() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "not-an-email", "alice"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("email");
    }

    @Test
    void updateUser_withInvalidEmail_returns400() {
        UserDto alice = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/" + alice.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request("Alice", "not-an-email", "alice")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("email");
    }

    // ── username ─────────────────────────────────────────────────

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
        ResponseEntity<UserDto> response = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getUsername()).isEqualTo("alice");
    }

    @Test
    void createUser_duplicateUsername_returns409() {
        restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/users", request("Alice2", "alice2@demo.com", "alice"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateUser_usernameIsIgnored_remainsUnchanged() {
        UserDto alice = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();

        // Attempt to change username via update — must be silently ignored
        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + alice.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request("Alice Updated", "alice@demo.com", "new_username")),
                UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo("alice");
    }

    // ── active ───────────────────────────────────────────────────

    @Test
    void createUser_isActiveByDefault() {
        ResponseEntity<UserDto> response = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class);

        assertThat(response.getBody().isActive()).isTrue();
    }

    @Test
    void updateUser_canDeactivateUser() {
        UserDto alice = restTemplate.postForEntity(
                "/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();

        UserRequest deactivate = request("Alice", "alice@demo.com", "alice");
        deactivate.setActive(false);
        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + alice.getId(), HttpMethod.PUT, new HttpEntity<>(deactivate), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isFalse();
    }

    // ── PATCH /api/v1/users/{id}/avatar ──────────────────────────

    @Test
    void updateAvatar_setsAvatarFileId() {
        UserDto alice = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();
        UUID fileId = UUID.randomUUID();

        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + alice.getId() + "/avatar",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("fileId", fileId)),
                UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAvatarFileId()).isEqualTo(fileId);
    }

    @Test
    void updateAvatar_clearAvatar_setsAvatarFileIdToNull() {
        UserDto alice = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();
        UUID fileId = UUID.randomUUID();
        restTemplate.exchange("/api/v1/users/" + alice.getId() + "/avatar",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("fileId", fileId)), UserDto.class);

        // Clear by passing null
        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + alice.getId() + "/avatar",
                HttpMethod.PATCH,
                new HttpEntity<>(new java.util.HashMap<>(Map.of())),
                UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAvatarFileId()).isNull();
    }

    @Test
    void updateAvatar_whenUserNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/" + UUID.randomUUID() + "/avatar",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("fileId", UUID.randomUUID())),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateAvatar_persistedOnSubsequentGet() {
        UserDto alice = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com", "alice"), UserDto.class).getBody();
        UUID fileId = UUID.randomUUID();
        restTemplate.exchange("/api/v1/users/" + alice.getId() + "/avatar",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("fileId", fileId)), UserDto.class);

        ResponseEntity<UserDto> response = restTemplate.getForEntity("/api/v1/users/" + alice.getId(), UserDto.class);

        assertThat(response.getBody().getAvatarFileId()).isEqualTo(fileId);
    }

    // ── Helper ────────────────────────────────────────────────────

    /** Deserializes a paginated user list response. */
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
}
