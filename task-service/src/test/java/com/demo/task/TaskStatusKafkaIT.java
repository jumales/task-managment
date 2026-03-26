package com.demo.task;

import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangeType;
import com.demo.task.client.UserClient;
import com.demo.task.model.OutboxEventType;
import com.demo.task.repository.OutboxRepository;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskStatusKafkaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @MockitoBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskProjectRepository projectRepository;

    @Autowired
    OutboxRepository outboxRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private UUID projectId;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        when(userClient.getUserById(ALICE_ID))
                .thenReturn(new UserDto(ALICE_ID, "Alice", "alice@demo.com", null, true, null, null));
        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Test Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();
    }

    // ── Outbox creation ───────────────────────────────────────────

    @Test
    void updateStatus_createsOutboxEvent() {
        TaskResponse created = createTask(TaskStatus.TODO);

        updateTask(created.getId(), TaskStatus.IN_PROGRESS);

        var events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(OutboxEventType.TASK_CHANGED);
        assertThat(events.get(0).getTopic()).isEqualTo("task-changed");
        assertThat(events.get(0).getPayload()).contains(TaskChangeType.STATUS_CHANGED.name());
        assertThat(events.get(0).getPayload()).contains("IN_PROGRESS");
    }

    @Test
    void updateWithoutStatusChange_doesNotCreateOutboxEvent() {
        TaskResponse created = createTask(TaskStatus.TODO);

        updateTask(created.getId(), TaskStatus.TODO);

        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    void multipleStatusChanges_createSeparateOutboxEvents() {
        TaskResponse created = createTask(TaskStatus.TODO);

        updateTask(created.getId(), TaskStatus.IN_PROGRESS);
        updateTask(created.getId(), TaskStatus.DONE);

        assertThat(outboxRepository.findAll()).hasSize(2);
    }

    // ── Kafka publishing ──────────────────────────────────────────

    @Test
    void updateStatus_outboxEventIsPublishedToKafka() {
        TaskResponse created = createTask(TaskStatus.TODO);

        updateTask(created.getId(), TaskStatus.IN_PROGRESS);

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(outboxRepository.findByPublishedFalse()).isEmpty()
        );
        assertThat(outboxRepository.findAll().get(0).isPublished()).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private TaskResponse createTask(TaskStatus status) {
        return restTemplate.postForEntity("/api/v1/tasks", request(status), TaskResponse.class).getBody();
    }

    private void updateTask(UUID id, TaskStatus status) {
        restTemplate.exchange("/api/v1/tasks/" + id, HttpMethod.PUT,
                new HttpEntity<>(request(status)), TaskResponse.class);
    }

    private TaskRequest request(TaskStatus status) {
        TaskRequest req = new TaskRequest();
        req.setTitle("Task");
        req.setDescription("desc");
        req.setStatus(status);
        req.setAssignedUserId(ALICE_ID);
        req.setProjectId(projectId);
        return req;
    }
}
