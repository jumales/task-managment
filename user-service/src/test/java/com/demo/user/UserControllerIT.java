package com.demo.user;

import com.demo.common.dto.UserDto;
import com.demo.common.dto.UserRequest;
import com.demo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class UserControllerIT {

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
    void getAllUsers_whenEmpty_returnsEmptyList() {
        ResponseEntity<UserDto[]> response = restTemplate.getForEntity("/api/v1/users", UserDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getAllUsers_returnsAllPersistedUsers() {
        restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com"), UserDto.class);
        restTemplate.postForEntity("/api/v1/users", request("Bob",   "bob@demo.com"),   UserDto.class);

        ResponseEntity<UserDto[]> response = restTemplate.getForEntity("/api/v1/users", UserDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    // ── GET /api/v1/users/{id} ───────────────────────────────────────

    @Test
    void getUserById_returnsUser() {
        UserDto created = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com"), UserDto.class).getBody();

        ResponseEntity<UserDto> response = restTemplate.getForEntity("/api/v1/users/" + created.getId(), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Alice");
        assertThat(response.getBody().getEmail()).isEqualTo("alice@demo.com");
        assertThat(response.getBody().getRoles()).isEmpty();
    }

    @Test
    void getUserById_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/users/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/v1/users ───────────────────────────────────────────

    @Test
    void createUser_persistsAndReturnsUser() {
        ResponseEntity<UserDto> response = restTemplate.postForEntity("/api/v1/users", request("Carol", "carol@demo.com"), UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Carol");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void createMultipleUsers_allArePersisted() {
        restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com"), UserDto.class);
        restTemplate.postForEntity("/api/v1/users", request("Bob",   "bob@demo.com"),   UserDto.class);

        assertThat(repository.count()).isEqualTo(2);
    }

    // ── PUT /api/v1/users/{id} ───────────────────────────────────────

    @Test
    void updateUser_updatesAllFields() {
        UserDto created = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com"), UserDto.class).getBody();

        ResponseEntity<UserDto> response = restTemplate.exchange(
                "/api/v1/users/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request("Alice Updated", "alice.new@demo.com")),
                UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Alice Updated");
        assertThat(response.getBody().getEmail()).isEqualTo("alice.new@demo.com");
    }

    // ── DELETE /api/v1/users/{id} ────────────────────────────────────

    @Test
    void deleteUser_removesUserFromDatabase() {
        UserDto created = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com"), UserDto.class).getBody();

        restTemplate.delete("/api/v1/users/" + created.getId());

        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void deleteUser_thenGetById_returns404() {
        UserDto created = restTemplate.postForEntity("/api/v1/users", request("Alice", "alice@demo.com"), UserDto.class).getBody();

        restTemplate.delete("/api/v1/users/" + created.getId());

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/users/" + created.getId(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helper ────────────────────────────────────────────────────

    private UserRequest request(String name, String email) {
        UserRequest req = new UserRequest();
        req.setName(name);
        req.setEmail(email);
        return req;
    }
}
