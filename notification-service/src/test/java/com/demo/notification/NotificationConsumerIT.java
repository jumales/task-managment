package com.demo.notification;

import com.demo.common.dto.ProjectNotificationTemplateResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangeType;
import com.demo.common.event.TaskChangedEvent;
import com.demo.notification.client.TaskServiceClient;
import com.demo.notification.client.UserClient;
import com.demo.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Testcontainers
@DirtiesContext
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                // Producer config for test KafkaTemplate — not needed in production (consumer-only service)
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
        }
)
class NotificationConsumerIT {

    /**
     * Overrides production security — permits all requests and injects an ADMIN authentication
     * so that @PreAuthorize checks pass.
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

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static GenericContainer<?> mailhog = new GenericContainer<>("mailhog/mailhog:v1.0.1")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v2/messages").forPort(8025));

    @DynamicPropertySource
    static void configureMailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailhog::getHost);
        registry.add("spring.mail.port", () -> mailhog.getMappedPort(1025));
    }

    @Autowired
    KafkaTemplate<String, TaskChangedEvent> kafkaTemplate;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    UserClient userClient;

    @MockBean
    TaskServiceClient taskServiceClient;

    private static final String TEST_EMAIL = "testuser@demo.com";
    private static final String TEST_NAME  = "Test User";

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        // Reset MailHog inbox before each test
        mailhogClient().delete(mailhogApiUrl() + "/api/v1/messages");
        // Default mock: return a user with a valid email
        when(userClient.getUserById(any(UUID.class)))
                .thenReturn(new UserDto(UUID.randomUUID(), TEST_NAME, TEST_EMAIL, "testuser", true, List.of(), null, "en"));
        // Default mock: no project template configured
        when(taskServiceClient.getTemplate(any(UUID.class), any(TaskChangeType.class)))
                .thenThrow(new RuntimeException("404 not found"));
    }

    // ── Consumer tests ────────────────────────────────────────────────────────

    @Test
    void consumeStatusChangedEvent_sendsEmailAndPersistsRecord() {
        UUID taskId    = UUID.randomUUID();
        UUID assignee  = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, assignee, null, "Test Task",
                        TaskStatus.TODO, TaskStatus.IN_PROGRESS));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(notificationRepository.findByTaskIdOrderBySentAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            List<Map<String, Object>> messages = getMailhogMessages();
            assertThat(messages).hasSize(1);
            assertThat(subjectOf(messages.get(0))).contains("IN_PROGRESS");
            assertThat(toOf(messages.get(0))).contains(TEST_EMAIL);
        });
    }

    @Test
    void consumeCommentAddedEvent_sendsEmailAndPersistsRecord() {
        UUID taskId   = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.commentAdded(taskId, assignee, null, "Test Task",
                        UUID.randomUUID(), "Great progress!"));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(notificationRepository.findByTaskIdOrderBySentAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            List<Map<String, Object>> messages = getMailhogMessages();
            assertThat(messages).hasSize(1);
            assertThat(subjectOf(messages.get(0))).contains("comment");
        });
    }

    @Test
    void consumePhaseChangedEvent_sendsEmailAndPersistsRecord() {
        UUID taskId   = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.phaseChanged(taskId, assignee, null, "Test Task",
                        UUID.randomUUID(), "Backlog", UUID.randomUUID(), "In Review"));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(notificationRepository.findByTaskIdOrderBySentAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            List<Map<String, Object>> messages = getMailhogMessages();
            assertThat(messages).hasSize(1);
            assertThat(subjectOf(messages.get(0))).contains("In Review");
        });
    }

    @Test
    void consumePlannedWorkCreatedEvent_notifiesPlannedWorkUser() {
        UUID taskId      = UUID.randomUUID();
        UUID plannedUser = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.plannedWorkCreated(taskId, null, "Test Task",
                        UUID.randomUUID(), plannedUser,
                        com.demo.common.dto.WorkType.DEVELOPMENT,
                        java.math.BigInteger.valueOf(8)));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(notificationRepository.findByTaskIdOrderBySentAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            assertThat(getMailhogMessages()).hasSize(1);
        });
    }

    @Test
    void consumeEventWithNoRecipient_doesNotSendEmail() {
        UUID taskId = UUID.randomUUID();

        // assignedUserId = null → no recipient → notification skipped
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, null, null, "Test Task",
                        TaskStatus.TODO, TaskStatus.DONE));

        // Wait a bit to confirm nothing arrives
        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.findByTaskIdOrderBySentAtAsc(taskId, Pageable.unpaged()).getContent()).isEmpty()
        );
        assertThat(getMailhogMessages()).isEmpty();
    }

    @Test
    void consumeTaskCreatedEvent_sendsEmailAndPersistsRecord() {
        UUID taskId   = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.taskCreated(taskId, assignee, projectId, "My New Task"));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(notificationRepository.findByTaskIdOrderBySentAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            List<Map<String, Object>> messages = getMailhogMessages();
            assertThat(messages).hasSize(1);
            assertThat(subjectOf(messages.get(0))).contains("My New Task");
        });
    }

    @Test
    void consumeEventWithProjectTemplate_usesTemplateContent() {
        UUID taskId    = UUID.randomUUID();
        UUID assignee  = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        // Configure mock to return a project-level template for STATUS_CHANGED.
        // Use doReturn to avoid invoking the already-stubbed thenThrow on this mock.
        org.mockito.Mockito.doReturn(new ProjectNotificationTemplateResponse(
                        UUID.randomUUID(), projectId, TaskChangeType.STATUS_CHANGED,
                        "Custom: task {taskTitle} changed to {toStatus}",
                        "Body for {taskTitle} in project {projectId}"))
                .when(taskServiceClient).getTemplate(projectId, TaskChangeType.STATUS_CHANGED);

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, assignee, projectId, "Template Task",
                        TaskStatus.TODO, TaskStatus.IN_PROGRESS));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(notificationRepository.findByTaskIdOrderBySentAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            List<Map<String, Object>> messages = getMailhogMessages();
            assertThat(messages).hasSize(1);
            // Subject should use the custom template with placeholders rendered
            assertThat(subjectOf(messages.get(0))).contains("Template Task");
            assertThat(subjectOf(messages.get(0))).contains("IN_PROGRESS");
        });
    }

    // ── Controller test ───────────────────────────────────────────────────────

    @Test
    void getNotificationsByTaskId_returnsHistory() {
        UUID taskId   = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, assignee, null, "Test Task",
                        TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.commentAdded(taskId, assignee, null, "Test Task",
                        UUID.randomUUID(), "A comment"));

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.findByTaskIdOrderBySentAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(2)
        );

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/notifications/tasks/" + taskId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("content")).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String mailhogApiUrl() {
        return "http://" + mailhog.getHost() + ":" + mailhog.getMappedPort(8025);
    }

    /**
     * Queries MailHog's REST API for all captured messages.
     * Uses a custom RestTemplate because MailHog returns Content-Type: text/json
     * which Spring's default converters don't accept.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMailhogMessages() {
        ResponseEntity<Map> response = mailhogClient().getForEntity(mailhogApiUrl() + "/api/v2/messages", Map.class);
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    /** Returns a RestTemplate that accepts the text/json content-type returned by MailHog. */
    private RestTemplate mailhogClient() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("text/json")));
        return new RestTemplate(List.of(converter));
    }

    @SuppressWarnings("unchecked")
    private String subjectOf(Map<String, Object> message) {
        Map<String, Object> content = (Map<String, Object>) message.get("Content");
        Map<String, List<String>> headers = (Map<String, List<String>>) content.get("Headers");
        return headers.get("Subject").get(0);
    }

    @SuppressWarnings("unchecked")
    private String toOf(Map<String, Object> message) {
        Map<String, Object> content = (Map<String, Object>) message.get("Content");
        Map<String, List<String>> headers = (Map<String, List<String>>) content.get("Headers");
        return headers.get("To").get(0);
    }
}
