package com.demo.notification;

import com.demo.common.dto.DevicePlatform;
import com.demo.common.dto.DeviceTokenRequest;
import com.demo.notification.client.TaskServiceClient;
import com.demo.notification.client.UserClient;
import com.demo.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@DirtiesContext
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.mail.host=localhost",
                "spring.mail.port=1025",
                // FCM disabled — no Firebase credentials needed
                "fcm.enabled=false",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
        }
)
class DeviceTokenControllerIT {

    /** Fixed user ID injected via the test security filter so @AuthenticationPrincipal Jwt resolves correctly. */
    private static final UUID TEST_USER_ID = UUID.randomUUID();

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
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
                            // Build a Jwt stub so @AuthenticationPrincipal Jwt resolves to our test user ID.
                            Jwt jwt = Jwt.withTokenValue("test-token")
                                    .header("alg", "none")
                                    .subject(TEST_USER_ID.toString())
                                    .claim("sub", TEST_USER_ID.toString())
                                    .build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                                            jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                            chain.doFilter(request, response);
                        }
                    }, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    DeviceTokenRepository repository;

    @MockBean
    UserClient userClient;

    @MockBean
    TaskServiceClient taskServiceClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void register_createsToken_returns201() {
        DeviceTokenRequest req = new DeviceTokenRequest();
        req.setToken("fcm-token-abc");
        req.setPlatform(DevicePlatform.ANDROID);
        req.setAppVersion("1.0.0");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/device-tokens", req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("token")).isEqualTo("fcm-token-abc");
        assertThat(response.getBody().get("platform")).isEqualTo("ANDROID");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void register_duplicateToken_doesNotDuplicate() {
        DeviceTokenRequest req = new DeviceTokenRequest();
        req.setToken("fcm-token-dup");
        req.setPlatform(DevicePlatform.ANDROID);

        restTemplate.postForEntity("/api/v1/device-tokens", req, Map.class);
        restTemplate.postForEntity("/api/v1/device-tokens", req, Map.class);

        assertThat(repository.findByUserIdAndDeletedAtIsNull(TEST_USER_ID)).hasSize(1);
    }

    @Test
    void listMine_returnsOnlyCallerTokens() {
        registerToken("token-1");
        registerToken("token-2");

        ResponseEntity<List> response = restTemplate.getForEntity("/api/v1/device-tokens/me", List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void delete_softDeletesToken_returns204() {
        registerToken("token-to-delete");
        assertThat(repository.findByUserIdAndDeletedAtIsNull(TEST_USER_ID)).hasSize(1);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/device-tokens/token-to-delete", HttpMethod.DELETE, null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(repository.findByUserIdAndDeletedAtIsNull(TEST_USER_ID)).isEmpty();
        // Row still exists (soft-delete)
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void rotate_softDeletesOldAndRegistersNew() {
        registerToken("old-token");

        DeviceTokenRequest newReq = new DeviceTokenRequest();
        newReq.setToken("new-token");
        newReq.setPlatform(DevicePlatform.ANDROID);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/device-tokens/old-token", HttpMethod.PUT,
                new HttpEntity<>(newReq), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("token")).isEqualTo("new-token");
        // Old token soft-deleted, new token active — only one active row
        assertThat(repository.findByUserIdAndDeletedAtIsNull(TEST_USER_ID)).hasSize(1);
        assertThat(repository.findByUserIdAndDeletedAtIsNull(TEST_USER_ID).get(0).getToken())
                .isEqualTo("new-token");
    }

    @Test
    void delete_unknownToken_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/device-tokens/nonexistent", HttpMethod.DELETE, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void registerToken(String tokenValue) {
        DeviceTokenRequest req = new DeviceTokenRequest();
        req.setToken(tokenValue);
        req.setPlatform(DevicePlatform.ANDROID);
        restTemplate.postForEntity("/api/v1/device-tokens", req, Map.class);
    }
}
