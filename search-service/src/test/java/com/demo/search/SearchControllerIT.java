package com.demo.search;

import com.demo.common.dto.TaskStatus;
import com.demo.common.event.TaskEvent;
import com.demo.common.event.UserEvent;
import com.demo.search.document.TaskDocument;
import com.demo.search.document.UserDocument;
import com.demo.search.service.TaskIndexService;
import com.demo.search.service.UserIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class SearchControllerIT {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.0"))
            .withEnv("xpack.security.enabled", "false");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    /**
     * Bypasses JWT authentication for all test requests.
     * Injects an ADMIN authentication so that {@code @PreAuthorize("isAuthenticated()")} passes.
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
                            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                                SecurityContextHolder.getContext().setAuthentication(
                                        new UsernamePasswordAuthenticationToken("test-user", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                                        new SimpleGrantedAuthority("ROLE_WEB_APP")))
                                );
                            }
                            chain.doFilter(request, response);
                        }
                    }, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TaskIndexService taskIndexService;

    @Autowired
    private UserIndexService userIndexService;

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws InterruptedException {
        // Index a task document directly (bypasses Kafka for speed)
        TaskEvent taskEvent = TaskEvent.created(TASK_ID,
                "TASK_1",
                "Implement login feature",
                "Build the authentication flow with OAuth2",
                TaskStatus.IN_PROGRESS,
                PROJECT_ID, "Alpha Project",
                null, null,
                UUID.randomUUID(), "Alice Smith",
                null, null);
        taskIndexService.index(taskEvent);

        // Index a user document directly
        UserEvent userEvent = UserEvent.created(USER_ID, "Bob Builder", "bob@example.com", "bob.builder", true);
        userIndexService.index(userEvent);

        // Give Elasticsearch time to refresh the index
        Thread.sleep(1500);
    }

    @Test
    void searchTasks_returnsMatchingTasksByTitle() {
        ResponseEntity<List<TaskDocument>> response = restTemplate.exchange(
                "/api/v1/search/tasks?q=login",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).anyMatch(t -> t.getId().equals(TASK_ID.toString()));
    }

    @Test
    void searchTasks_returnsMatchingTasksByDescription() {
        ResponseEntity<List<TaskDocument>> response = restTemplate.exchange(
                "/api/v1/search/tasks?q=authentication",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).anyMatch(t -> t.getId().equals(TASK_ID.toString()));
    }

    @Test
    void searchTasks_returnsEmptyListForUnmatchedQuery() {
        ResponseEntity<List<TaskDocument>> response = restTemplate.exchange(
                "/api/v1/search/tasks?q=zxqwerty99999",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }

    @Test
    void searchUsers_returnsMatchingUsersByName() {
        ResponseEntity<List<UserDocument>> response = restTemplate.exchange(
                "/api/v1/search/users?q=Bob",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).anyMatch(u -> u.getId().equals(USER_ID.toString()));
    }

    @Test
    void searchUsers_returnsMatchingUsersByEmail() {
        ResponseEntity<List<UserDocument>> response = restTemplate.exchange(
                "/api/v1/search/users?q=bob@example.com",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).anyMatch(u -> u.getId().equals(USER_ID.toString()));
    }

    @Test
    void searchUsers_returnsEmptyListForUnmatchedQuery() {
        ResponseEntity<List<UserDocument>> response = restTemplate.exchange(
                "/api/v1/search/users?q=zxqwerty99999",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }
}
