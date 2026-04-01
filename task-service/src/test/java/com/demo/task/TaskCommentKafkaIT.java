package com.demo.task;

import com.demo.common.dto.TaskCommentRequest;
import com.demo.common.dto.TaskCommentResponse;
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
import com.demo.task.repository.TaskCommentRepository;
import com.demo.task.repository.TaskPhaseRepository;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskTimelineRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
class TaskCommentKafkaIT {

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
    TaskCommentRepository commentRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    TaskTimelineRepository timelineRepository;

    @Autowired
    TaskPhaseRepository phaseRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private UUID projectId;
    private UUID phaseId;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        commentRepository.deleteAll();
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();
        when(userClient.getUserById(ALICE_ID))
                .thenReturn(new UserDto(ALICE_ID, "Alice", "alice@demo.com", null, true, null, null, "en"));
        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Test Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();

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

    // ── Adding comments ───────────────────────────────────────────

    @Test
    void addComment_persistsCommentAndCreatesOutboxEvent() {
        TaskResponse task = createTask();
        // Task creation writes a TASK_CREATED outbox event; clear it to test only comment events
        outboxRepository.deleteAll();

        TaskCommentResponse comment = addComment(task.getId(), "First comment");

        assertThat(comment.getContent()).isEqualTo("First comment");
        assertThat(comment.getId()).isNotNull();

        // Filter to task-changed topic only (lifecycle events go to task-events topic)
        var events = changedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(OutboxEventType.TASK_CHANGED);
        assertThat(events.get(0).getPayload()).contains(TaskChangeType.COMMENT_ADDED.name());
        assertThat(events.get(0).getPayload()).contains("First comment");
    }

    @Test
    void addMultipleComments_allPersistedInOrder() {
        TaskResponse task = createTask();
        // Task creation writes a TASK_CREATED outbox event; clear it to test only comment events
        outboxRepository.deleteAll();

        addComment(task.getId(), "First comment");
        addComment(task.getId(), "Second comment");
        addComment(task.getId(), "Third comment");

        TaskCommentResponse[] comments = getComments(task.getId());
        assertThat(comments).hasSize(3);
        assertThat(comments).extracting("content")
                .containsExactly("First comment", "Second comment", "Third comment");
        assertThat(changedEvents()).hasSize(3);
    }

    @Test
    void addComment_onNonExistentTask_returns404() {
        var response = restTemplate.postForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/comments",
                commentRequest("Some comment"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Kafka publishing ──────────────────────────────────────────

    @Test
    void addComment_outboxEventIsPublishedToKafka() {
        TaskResponse task = createTask();

        addComment(task.getId(), "Published comment");

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(outboxRepository.findByPublishedFalse()).isEmpty()
        );
    }

    // ── Delete guard ──────────────────────────────────────────────

    @Test
    void deleteTask_withActiveComments_returns409() {
        TaskResponse task = createTask();
        addComment(task.getId(), "Comment A");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + task.getId(), org.springframework.http.HttpMethod.DELETE,
                org.springframework.http.HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())).hasSize(1);
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Returns only outbox events for the {@code task-changed} topic (audit events). */
    private java.util.List<OutboxEvent> changedEvents() {
        return outboxRepository.findAll().stream()
                .filter(e -> KafkaTopics.TASK_CHANGED.equals(e.getTopic()))
                .toList();
    }

    private TaskResponse createTask() {
        TaskRequest req = new TaskRequest();
        req.setTitle("Task");
        req.setDescription("desc");
        req.setStatus(TaskStatus.TODO);
        req.setAssignedUserId(ALICE_ID);
        req.setProjectId(projectId);
        req.setPhaseId(phaseId);
        req.setPlannedStart(java.time.Instant.parse("2026-04-01T08:00:00Z"));
        req.setPlannedEnd(java.time.Instant.parse("2026-04-30T17:00:00Z"));
        return restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class).getBody();
    }

    private TaskCommentResponse addComment(UUID taskId, String content) {
        return restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/comments",
                commentRequest(content),
                TaskCommentResponse.class).getBody();
    }

    private TaskCommentResponse[] getComments(UUID taskId) {
        return restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/comments",
                TaskCommentResponse[].class).getBody();
    }

    private TaskCommentRequest commentRequest(String content) {
        TaskCommentRequest req = new TaskCommentRequest();
        req.setContent(content);
        return req;
    }
}
