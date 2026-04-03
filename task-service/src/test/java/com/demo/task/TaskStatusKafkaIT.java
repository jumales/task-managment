package com.demo.task;

import com.demo.common.dto.TaskPhaseName;
import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangeType;
import com.demo.task.client.UserClient;
import com.demo.common.config.KafkaTopics;
import com.demo.task.model.OutboxEvent;
import com.demo.task.model.OutboxEventType;
import com.demo.task.repository.OutboxRepository;
import com.demo.task.repository.TaskParticipantRepository;
import com.demo.task.repository.TaskPhaseRepository;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskTimelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
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
@DirtiesContext
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

    @Autowired
    TaskParticipantRepository participantRepository;

    @Autowired
    TaskTimelineRepository timelineRepository;

    @Autowired
    TaskPhaseRepository phaseRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private UUID projectId;
    private UUID phaseId;
    private UUID planningPhaseId;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        outboxRepository.deleteAll();
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();
        when(userClient.getUserById(ALICE_ID))
                .thenReturn(new UserDto(ALICE_ID, "Alice", "alice@demo.com", null, true, null, null, "en"));
        when(userClient.getUserById(TestSecurityConfig.TEST_USER_ID))
                .thenReturn(new UserDto(TestSecurityConfig.TEST_USER_ID, "Test Admin", "admin@test.com", null, true, null, null, "en"));
        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Test Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();

        TaskPhaseRequest planningPhaseReq = new TaskPhaseRequest();
        planningPhaseReq.setName(TaskPhaseName.PLANNING);
        planningPhaseReq.setProjectId(projectId);
        planningPhaseId = restTemplate.postForEntity("/api/v1/phases", planningPhaseReq, TaskPhaseResponse.class).getBody().getId();

        TaskPhaseRequest phaseReq = new TaskPhaseRequest();
        phaseReq.setName(TaskPhaseName.BACKLOG);
        phaseReq.setProjectId(projectId);
        phaseId = restTemplate.postForEntity("/api/v1/phases", phaseReq, TaskPhaseResponse.class).getBody().getId();
        TaskProjectRequest defaultPhaseReq = new TaskProjectRequest();
        defaultPhaseReq.setName("Test Project");
        defaultPhaseReq.setDefaultPhaseId(phaseId);
        restTemplate.exchange("/api/v1/projects/" + projectId, HttpMethod.PUT,
                new HttpEntity<>(defaultPhaseReq), TaskProjectResponse.class);
    }

    // ── Outbox creation ───────────────────────────────────────────

    @Test
    void updateStatus_createsOutboxEvent() {
        TaskResponse created = createTask(TaskStatus.TODO);
        // Task creation writes a TASK_CREATED outbox event; clear it to test only status-change events
        outboxRepository.deleteAll();

        updateTask(created.getId(), TaskStatus.IN_PROGRESS);

        // Filter to task-changed topic only (lifecycle events go to task-events topic)
        var events = changedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(OutboxEventType.TASK_CHANGED);
        assertThat(events.get(0).getTopic()).isEqualTo("task-changed");
        assertThat(events.get(0).getPayload()).contains(TaskChangeType.STATUS_CHANGED.name());
        assertThat(events.get(0).getPayload()).contains("IN_PROGRESS");
    }

    @Test
    void updateWithoutStatusChange_doesNotCreateOutboxEvent() {
        TaskResponse created = createTask(TaskStatus.TODO);
        // Task creation writes a TASK_CREATED outbox event; clear it to test only status-change events
        outboxRepository.deleteAll();

        updateTask(created.getId(), TaskStatus.TODO);

        // Only task-changed events; task-events (lifecycle) are expected but not the concern here
        assertThat(changedEvents()).isEmpty();
    }

    @Test
    void multipleStatusChanges_createSeparateOutboxEvents() {
        TaskResponse created = createTask(TaskStatus.TODO);
        // Task creation writes a TASK_CREATED outbox event; clear it to test only status-change events
        outboxRepository.deleteAll();

        updateTask(created.getId(), TaskStatus.IN_PROGRESS);
        updateTask(created.getId(), TaskStatus.DONE);

        assertThat(changedEvents()).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Returns only outbox events for the {@code task-changed} topic (audit events). */
    private java.util.List<OutboxEvent> changedEvents() {
        return outboxRepository.findAll().stream()
                .filter(e -> KafkaTopics.TASK_CHANGED.equals(e.getTopic()))
                .toList();
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
        // Use planningPhaseId so updates keep the task in PLANNING, preventing phase-change outbox events
        // from interfering with status-change outbox event assertions.
        req.setPhaseId(planningPhaseId);
        req.setPlannedStart(java.time.Instant.parse("2026-04-01T08:00:00Z"));
        req.setPlannedEnd(java.time.Instant.parse("2026-04-30T17:00:00Z"));
        return req;
    }
}
