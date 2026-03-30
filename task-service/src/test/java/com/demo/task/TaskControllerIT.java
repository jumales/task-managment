package com.demo.task;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskCommentRequest;
import com.demo.common.dto.TaskCommentResponse;
import com.demo.common.dto.TaskParticipantRole;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskSummaryResponse;
import com.demo.common.dto.TaskType;
import com.demo.common.dto.UserDto;
import com.demo.task.client.UserClient;
import com.demo.task.repository.TaskParticipantRepository;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskTimelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskRepository repository;

    @Autowired
    TaskProjectRepository projectRepository;

    @Autowired
    TaskParticipantRepository participantRepository;

    @Autowired
    TaskTimelineRepository timelineRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID   = UUID.randomUUID();

    private UserDto alice;
    private UserDto bob;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        timelineRepository.deleteAll();
        repository.deleteAll();
        projectRepository.deleteAll();

        alice = new UserDto(ALICE_ID, "Alice Johnson", "alice@demo.com", null, true, null, null, "en");
        bob   = new UserDto(BOB_ID,   "Bob Smith",     "bob@demo.com",   null, true, null, null, "en");
        UserDto testAdmin = new UserDto(TestSecurityConfig.TEST_USER_ID, "Test Admin", "admin@test.com", null, true, null, null, "en");

        when(userClient.getUserById(ALICE_ID)).thenReturn(alice);
        when(userClient.getUserById(BOB_ID)).thenReturn(bob);
        when(userClient.getUserById(TestSecurityConfig.TEST_USER_ID)).thenReturn(testAdmin);
        // Batch fetch used by toResponseList()
        when(userClient.getUsersByIds(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return List.of(alice, bob, testAdmin).stream().filter(u -> ids.contains(u.getId())).toList();
        });

        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Default Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();
    }

    // ── GET /api/v1/tasks ────────────────────────────────────────────

    @Test
    void getAllTasks_whenEmpty_returnsEmptyPage() {
        ResponseEntity<PageResponse<TaskSummaryResponse>> response = getTaskPage("/api/v1/tasks");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isZero();
    }

    @Test
    void getAllTasks_returnsTasksWithUserDetails() {
        restTemplate.postForEntity("/api/v1/tasks", request("Setup CI", "Configure pipeline", TaskStatus.DONE, ALICE_ID), TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Write tests", "Unit tests", TaskStatus.TODO, BOB_ID), TaskResponse.class);

        ResponseEntity<PageResponse<TaskSummaryResponse>> response = getTaskPage("/api/v1/tasks");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(2);
        assertThat(response.getBody().getContent().get(0).getAssignedUserName()).isNotNull();
    }

    // ── GET /api/v1/tasks/{id} ───────────────────────────────────────

    @Test
    void getTaskById_returnsTaskWithUserDetails() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("Implement login", "JWT auth", TaskStatus.IN_PROGRESS, BOB_ID), TaskResponse.class).getBody();

        ResponseEntity<TaskResponse> response = restTemplate.getForEntity("/api/v1/tasks/" + created.getId(), TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTitle()).isEqualTo("Implement login");
        assertThat(response.getBody().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.getBody().getParticipants()).anyMatch(p ->
                p.getRole() == TaskParticipantRole.ASSIGNEE && "Bob Smith".equals(p.getUserName()));
    }

    @Test
    void getTaskById_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/tasks/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/v1/tasks?userId={id} ────────────────────────────────

    @Test
    void getTasksByUserId_returnsOnlyTasksAssignedToThatUser() {
        restTemplate.postForEntity("/api/v1/tasks", request("Task A", "For Alice",      TaskStatus.TODO, ALICE_ID), TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Task B", "For Bob",        TaskStatus.TODO, BOB_ID),   TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Task C", "Also for Alice", TaskStatus.DONE, ALICE_ID), TaskResponse.class);

        ResponseEntity<PageResponse<TaskSummaryResponse>> response = getTaskPage("/api/v1/tasks?userId=" + ALICE_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(2);
        assertThat(response.getBody().getContent()).allSatisfy(t ->
                assertThat(t.getAssignedUserId()).isEqualTo(ALICE_ID));
    }

    // ── GET /api/v1/tasks?status={status} ───────────────────────────

    @Test
    void getTasksByStatus_returnsOnlyMatchingTasks() {
        restTemplate.postForEntity("/api/v1/tasks", request("Task A", "desc", TaskStatus.TODO,        ALICE_ID), TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Task B", "desc", TaskStatus.IN_PROGRESS, BOB_ID),   TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Task C", "desc", TaskStatus.TODO,        ALICE_ID), TaskResponse.class);

        ResponseEntity<PageResponse<TaskSummaryResponse>> response = getTaskPage("/api/v1/tasks?status=TODO");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(2);
        assertThat(response.getBody().getContent()).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TaskStatus.TODO));
    }

    @Test
    void getTasksByStatus_caseInsensitive_returnsMatchingTasks() {
        restTemplate.postForEntity("/api/v1/tasks", request("Task A", "desc", TaskStatus.DONE, ALICE_ID), TaskResponse.class);

        ResponseEntity<PageResponse<TaskSummaryResponse>> response = getTaskPage("/api/v1/tasks?status=done");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(1);
    }

    // ── POST /api/v1/tasks ───────────────────────────────────────────

    @Test
    void createTask_persistsAndReturnsTask() {
        ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/api/v1/tasks", request("New feature", "Implement OAuth2", TaskStatus.TODO, ALICE_ID), TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("New feature");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void createTask_withTypeAndProgress_persistsBothFields() {
        TaskRequest req = request("Bug fix task", "Fix NPE", TaskStatus.TODO, ALICE_ID);
        req.setType(TaskType.BUG_FIXING);
        req.setProgress(25);

        ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getType()).isEqualTo(TaskType.BUG_FIXING);
        assertThat(response.getBody().getProgress()).isEqualTo(25);
    }

    @Test
    void updateTask_updatesTypeAndProgress() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("Task", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();

        TaskRequest update = request("Task", "desc", TaskStatus.IN_PROGRESS, ALICE_ID);
        update.setType(TaskType.FEATURE);
        update.setProgress(80);

        ResponseEntity<TaskResponse> response = restTemplate.exchange(
                "/api/v1/tasks/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getType()).isEqualTo(TaskType.FEATURE);
        assertThat(response.getBody().getProgress()).isEqualTo(80);
    }

    @Test
    void createTask_withNonExistentUser_returns500() {
        when(userClient.getUserById(any(UUID.class))).thenThrow(new RuntimeException("User not found"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/tasks", request("Ghost task", "desc", TaskStatus.TODO, UUID.randomUUID()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void createTask_withoutPlannedStart_returns400() {
        TaskRequest req = request("Task", "desc", TaskStatus.TODO, ALICE_ID);
        req.setPlannedStart(null);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/tasks", req, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void createTask_withoutPlannedEnd_returns400() {
        TaskRequest req = request("Task", "desc", TaskStatus.TODO, ALICE_ID);
        req.setPlannedEnd(null);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/tasks", req, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void createTask_withPlannedStartAfterPlannedEnd_returns400() {
        TaskRequest req = request("Task", "desc", TaskStatus.TODO, ALICE_ID);
        req.setPlannedStart(Instant.parse("2026-05-01T00:00:00Z"));
        req.setPlannedEnd(Instant.parse("2026-04-01T00:00:00Z"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/tasks", req, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void createTask_createsInitialPlannedTimelineEntries() {
        TaskRequest req = request("Task with timeline", "desc", TaskStatus.TODO, ALICE_ID);
        req.setPlannedStart(Instant.parse("2026-04-01T08:00:00Z"));
        req.setPlannedEnd(Instant.parse("2026-04-30T17:00:00Z"));
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class).getBody();

        ResponseEntity<com.demo.common.dto.TaskTimelineResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + created.getId() + "/timelines",
                com.demo.common.dto.TaskTimelineResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).anyMatch(e -> e.getState() == com.demo.common.dto.TimelineState.PLANNED_START
                && e.getTimestamp().equals(Instant.parse("2026-04-01T08:00:00Z")));
        assertThat(response.getBody()).anyMatch(e -> e.getState() == com.demo.common.dto.TimelineState.PLANNED_END
                && e.getTimestamp().equals(Instant.parse("2026-04-30T17:00:00Z")));
    }

    // ── PUT /api/v1/tasks/{id} ───────────────────────────────────────

    @Test
    void updateTask_updatesAllFields() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("Old title", "Old desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();

        ResponseEntity<TaskResponse> response = restTemplate.exchange(
                "/api/v1/tasks/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request("New title", "New desc", TaskStatus.IN_PROGRESS, BOB_ID)),
                TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTitle()).isEqualTo("New title");
        assertThat(response.getBody().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.getBody().getParticipants()).anyMatch(p ->
                p.getRole() == TaskParticipantRole.ASSIGNEE && BOB_ID.equals(p.getUserId()));
    }

    // ── DELETE /api/v1/tasks/{id} ────────────────────────────────────

    @Test
    void deleteTask_removesTaskFromDatabase() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("To delete", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();

        restTemplate.delete("/api/v1/tasks/" + created.getId());

        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void deleteTask_thenGetById_returns404() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("To delete", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();

        restTemplate.delete("/api/v1/tasks/" + created.getId());

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/tasks/" + created.getId(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteProject_withActiveTasks_returns409() {
        restTemplate.postForEntity("/api/v1/tasks", request("Blocking task", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repository.count()).isEqualTo(1);
    }

    // ── GET /api/v1/tasks/{id}/comments ─────────────────────────────

    @Test
    void getComments_whenNoComments_returnsEmptyList() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("Task", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();

        ResponseEntity<TaskCommentResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + created.getId() + "/comments", TaskCommentResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void addComment_returnsCreatedComment() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("Task", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();

        ResponseEntity<TaskCommentResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + created.getId() + "/comments",
                comment("Great progress!"),
                TaskCommentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getContent()).isEqualTo("Great progress!");
        assertThat(response.getBody().getId()).isNotNull();
    }

    @Test
    void addComment_thenGetComments_returnsComment() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("Task", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();
        restTemplate.postForEntity("/api/v1/tasks/" + created.getId() + "/comments",
                comment("First comment"), TaskCommentResponse.class);

        ResponseEntity<TaskCommentResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + created.getId() + "/comments", TaskCommentResponse[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getContent()).isEqualTo("First comment");
    }

    // ── Resilience ────────────────────────────────────────────────

    @Test
    void getAllTasks_whenUserServiceDown_returnsTasksWithNullUser() {
        restTemplate.postForEntity("/api/v1/tasks", request("Orphan task", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class);
        when(userClient.getUsersByIds(anyList())).thenThrow(new RuntimeException("user-service unavailable"));

        ResponseEntity<PageResponse<TaskSummaryResponse>> response = getTaskPage("/api/v1/tasks");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(1);
        // assignedUserName is null when user-service is unavailable
        assertThat(response.getBody().getContent().get(0).getAssignedUserName()).isNull();
    }

    // ── Helper ────────────────────────────────────────────────────

    /** Deserializes a paginated task list (summary) response. */
    private ResponseEntity<PageResponse<TaskSummaryResponse>> getTaskPage(String url) {
        return restTemplate.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
    }

    private TaskRequest request(String title, String description, TaskStatus status, UUID userId) {
        TaskRequest req = new TaskRequest();
        req.setTitle(title);
        req.setDescription(description);
        req.setStatus(status);
        req.setAssignedUserId(userId);
        req.setProjectId(projectId);
        req.setPlannedStart(Instant.parse("2026-04-01T08:00:00Z"));
        req.setPlannedEnd(Instant.parse("2026-04-30T17:00:00Z"));
        return req;
    }

    private TaskCommentRequest comment(String content) {
        TaskCommentRequest req = new TaskCommentRequest();
        req.setContent(content);
        return req;
    }
}
