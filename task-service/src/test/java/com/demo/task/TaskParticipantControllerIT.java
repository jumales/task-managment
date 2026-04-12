package com.demo.task;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskBookedWorkRequest;
import com.demo.common.dto.TaskCommentRequest;
import com.demo.common.dto.TaskParticipantResponse;
import com.demo.common.dto.TaskParticipantRole;
import com.demo.common.dto.TaskPhaseName;
import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskPhaseUpdateRequest;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskType;
import com.demo.common.dto.UserDto;
import com.demo.common.dto.WorkType;
import com.demo.task.client.UserClient;
import com.demo.task.repository.TaskCommentRepository;
import com.demo.task.repository.TaskParticipantRepository;
import com.demo.task.repository.TaskPhaseRepository;
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
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link com.demo.task.controller.TaskParticipantController}.
 * Covers Watch/Unwatch actions and auto-add-as-CONTRIBUTOR behavior for comments and booked work.
 */
@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskParticipantControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskProjectRepository projectRepository;

    @Autowired
    TaskParticipantRepository participantRepository;

    @Autowired
    TaskCommentRepository commentRepository;

    @Autowired
    TaskTimelineRepository timelineRepository;

    @Autowired
    TaskPhaseRepository phaseRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID   = UUID.randomUUID();

    private UserDto alice;
    private UserDto bob;
    private String taskId;
    private UUID phaseId;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        commentRepository.deleteAll();
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();

        alice = new UserDto(ALICE_ID, "Alice Johnson", "alice@demo.com", null, true, null, "en", List.of());
        bob   = new UserDto(BOB_ID,   "Bob Smith",     "bob@demo.com",   null, true, null, "en", List.of());
        UserDto testAdmin = new UserDto(TestSecurityConfig.TEST_USER_ID, "Test Admin", "admin@test.com", null, true, null, "en", List.of());

        when(userClient.getUserById(ALICE_ID)).thenReturn(alice);
        when(userClient.getUserById(BOB_ID)).thenReturn(bob);
        when(userClient.getUserById(TestSecurityConfig.TEST_USER_ID)).thenReturn(testAdmin);
        when(userClient.getUsersByIds(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return List.of(alice, bob, testAdmin).stream().filter(u -> ids.contains(u.getId())).toList();
        });

        // Create a project and a task used across tests
        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Test Project");
        UUID projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();

        TaskPhaseRequest phaseReq = new TaskPhaseRequest();
        phaseReq.setName(TaskPhaseName.BACKLOG);
        phaseReq.setProjectId(projectId);
        phaseId = restTemplate.postForEntity("/api/v1/phases", phaseReq, TaskPhaseResponse.class).getBody().getId();

        TaskProjectRequest defaultPhaseProjectReq = new TaskProjectRequest();
        defaultPhaseProjectReq.setName("Test Project");
        defaultPhaseProjectReq.setDefaultPhaseId(phaseId);
        restTemplate.exchange("/api/v1/projects/" + projectId, HttpMethod.PUT,
                new HttpEntity<>(defaultPhaseProjectReq), TaskProjectResponse.class);

        TaskRequest taskReq = new TaskRequest();
        taskReq.setTitle("Sample Task");
        taskReq.setDescription("desc");
        taskReq.setStatus(TaskStatus.TODO);
        taskReq.setAssignedUserId(ALICE_ID);
        taskReq.setProjectId(projectId);
        taskReq.setPhaseId(phaseId);
        taskReq.setType(TaskType.FEATURE);
        taskReq.setPlannedStart(Instant.parse("2026-04-01T08:00:00Z"));
        taskReq.setPlannedEnd(Instant.parse("2026-04-30T17:00:00Z"));
        taskId = restTemplate.postForEntity("/api/v1/tasks", taskReq, TaskResponse.class)
                .getBody().getId().toString();
    }

    // ── GET /api/v1/tasks/{taskId}/participants ──────────────────────────────

    @Test
    void getParticipants_afterTaskCreate_returnsCreatorAndAssignee() {
        ResponseEntity<TaskParticipantResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                TaskParticipantResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).anyMatch(p ->
                p.getRole() == TaskParticipantRole.CREATOR
                && TestSecurityConfig.TEST_USER_ID.equals(p.getUserId()));
        assertThat(response.getBody()).anyMatch(p ->
                p.getRole() == TaskParticipantRole.ASSIGNEE
                && ALICE_ID.equals(p.getUserId()));
    }

    // ── POST /api/v1/tasks/{taskId}/participants/watch ───────────────────────

    @Test
    void watch_addsAuthenticatedUserAsWatcher() {
        // TEST_USER_ID is already CREATOR because they created the task in @BeforeEach.
        // Remove that entry so the user is a clean non-participant and the watch endpoint
        // can add them as a fresh WATCHER.
        participantRepository.findByTaskId(UUID.fromString(taskId))
                .stream()
                .filter(p -> p.getUserId().equals(TestSecurityConfig.TEST_USER_ID))
                .forEach(p -> participantRepository.deleteById(p.getId()));

        ResponseEntity<TaskParticipantResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/participants/watch",
                null,
                TaskParticipantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getRole()).isEqualTo(TaskParticipantRole.WATCHER);
        assertThat(response.getBody().getUserId()).isEqualTo(TestSecurityConfig.TEST_USER_ID);
    }

    @Test
    void watch_whenUserAlreadyParticipant_returnsExistingEntry() {
        // TEST_USER_ID is already CREATOR; watching should return that existing entry without creating a new one
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/participants/watch", null, TaskParticipantResponse.class);

        ResponseEntity<TaskParticipantResponse[]> list = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                TaskParticipantResponse[].class);

        // Still only 2 entries (CREATOR + ASSIGNEE); no duplicate WATCHER added
        assertThat(list.getBody()).hasSize(2);
    }

    // ── DELETE /api/v1/tasks/{taskId}/participants/{participantId} ───────────

    @Test
    void unwatch_ownWatcherEntry_removesIt() {
        // Add a new task where TEST_USER_ID is not CREATOR so we can add a clean WATCHER entry
        // Use BOB as creator+assignee and then watch with TEST_USER_ID
        TaskRequest taskReq = new TaskRequest();
        taskReq.setTitle("Watch Task");
        taskReq.setDescription("desc");
        taskReq.setStatus(TaskStatus.TODO);
        taskReq.setAssignedUserId(BOB_ID);
        taskReq.setProjectId(restTemplate.exchange("/api/v1/projects", HttpMethod.GET, null,
                new ParameterizedTypeReference<PageResponse<TaskProjectResponse>>() {})
                .getBody().getContent().get(0).getId());
        taskReq.setPhaseId(phaseId);
        taskReq.setType(TaskType.FEATURE);
        taskReq.setPlannedStart(Instant.parse("2026-04-01T08:00:00Z"));
        taskReq.setPlannedEnd(Instant.parse("2026-04-30T17:00:00Z"));
        String watchTaskId = restTemplate.postForEntity("/api/v1/tasks", taskReq, TaskResponse.class)
                .getBody().getId().toString();

        // Clear the auto-added CREATOR participant so TEST_USER_ID can be added as a fresh WATCHER
        participantRepository.findByTaskId(UUID.fromString(watchTaskId))
                .stream()
                .filter(p -> p.getUserId().equals(TestSecurityConfig.TEST_USER_ID))
                .forEach(p -> participantRepository.deleteById(p.getId()));

        // Now watch — should create a WATCHER entry
        TaskParticipantResponse watcherEntry = restTemplate.postForEntity(
                "/api/v1/tasks/" + watchTaskId + "/participants/watch",
                null,
                TaskParticipantResponse.class).getBody();
        assertThat(watcherEntry.getRole()).isEqualTo(TaskParticipantRole.WATCHER);

        // Unwatch
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                "/api/v1/tasks/" + watchTaskId + "/participants/" + watcherEntry.getId(),
                HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Participant should be gone
        ResponseEntity<TaskParticipantResponse[]> list = restTemplate.getForEntity(
                "/api/v1/tasks/" + watchTaskId + "/participants",
                TaskParticipantResponse[].class);
        assertThat(list.getBody()).noneMatch(p -> p.getId().equals(watcherEntry.getId()));
    }

    @Test
    void removeParticipant_creatorEntry_returns422() {
        // The CREATOR entry was auto-created for TEST_USER_ID; removing it must be blocked
        TaskParticipantResponse[] participants = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                TaskParticipantResponse[].class).getBody();
        String creatorParticipantId = java.util.Arrays.stream(participants)
                .filter(p -> p.getRole() == TaskParticipantRole.CREATOR)
                .findFirst().orElseThrow().getId().toString();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/participants/" + creatorParticipantId,
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void removeParticipant_assigneeEntry_returns422() {
        // ASSIGNEE for Alice must be blocked
        TaskParticipantResponse[] participants = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                TaskParticipantResponse[].class).getBody();
        String assigneeParticipantId = java.util.Arrays.stream(participants)
                .filter(p -> p.getRole() == TaskParticipantRole.ASSIGNEE)
                .findFirst().orElseThrow().getId().toString();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/participants/" + assigneeParticipantId,
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void removeParticipant_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/participants/" + UUID.randomUUID(),
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Auto-add as CONTRIBUTOR ──────────────────────────────────────────────

    @Test
    void addComment_autoAddsAuthorAsContributor() {
        // TEST_USER_ID is already CREATOR; posting a comment must NOT add a duplicate entry
        TaskCommentRequest req = new TaskCommentRequest();
        req.setContent("My comment");
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/comments", req, Object.class);

        ResponseEntity<TaskParticipantResponse[]> list = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                TaskParticipantResponse[].class);

        // Still 2 (CREATOR + ASSIGNEE); no duplicate CONTRIBUTOR for the same user
        assertThat(list.getBody()).hasSize(2);

        // A different user commenting on a separate task should be added as CONTRIBUTOR
        TaskRequest taskReq = new TaskRequest();
        taskReq.setTitle("Comment Task");
        taskReq.setDescription("desc");
        taskReq.setStatus(TaskStatus.TODO);
        taskReq.setAssignedUserId(ALICE_ID);
        taskReq.setProjectId(restTemplate.exchange("/api/v1/projects", HttpMethod.GET, null,
                new ParameterizedTypeReference<PageResponse<TaskProjectResponse>>() {})
                .getBody().getContent().get(0).getId());
        taskReq.setPhaseId(phaseId);
        taskReq.setType(TaskType.FEATURE);
        taskReq.setPlannedStart(Instant.parse("2026-04-01T08:00:00Z"));
        taskReq.setPlannedEnd(Instant.parse("2026-04-30T17:00:00Z"));
        String commentTaskId = restTemplate.postForEntity("/api/v1/tasks", taskReq, TaskResponse.class)
                .getBody().getId().toString();

        // Remove the auto-created CREATOR entry so TEST_USER_ID appears fresh
        participantRepository.findByTaskId(UUID.fromString(commentTaskId))
                .stream()
                .filter(p -> p.getUserId().equals(TestSecurityConfig.TEST_USER_ID))
                .forEach(p -> participantRepository.deleteById(p.getId()));

        restTemplate.postForEntity("/api/v1/tasks/" + commentTaskId + "/comments", req, Object.class);

        ResponseEntity<TaskParticipantResponse[]> list2 = restTemplate.getForEntity(
                "/api/v1/tasks/" + commentTaskId + "/participants",
                TaskParticipantResponse[].class);

        assertThat(list2.getBody()).anyMatch(p ->
                p.getRole() == TaskParticipantRole.CONTRIBUTOR
                && TestSecurityConfig.TEST_USER_ID.equals(p.getUserId()));
    }

    @Test
    void createBookedWork_autoAddsUserAsContributor() {
        // Tasks start in PLANNING phase; booked work is blocked in PLANNING.
        // Advance to BACKLOG so the booked-work call is allowed.
        TaskPhaseUpdateRequest phaseUpdate = new TaskPhaseUpdateRequest();
        phaseUpdate.setPhaseId(phaseId);
        restTemplate.exchange("/api/v1/tasks/" + taskId + "/phase",
                HttpMethod.PATCH, new HttpEntity<>(phaseUpdate), TaskResponse.class);

        // Remove TEST_USER_ID's CREATOR entry so they are fresh on this task
        participantRepository.findByTaskId(UUID.fromString(taskId))
                .stream()
                .filter(p -> p.getUserId().equals(TestSecurityConfig.TEST_USER_ID))
                .forEach(p -> participantRepository.deleteById(p.getId()));

        TaskBookedWorkRequest req = new TaskBookedWorkRequest();
        req.setWorkType(WorkType.DEVELOPMENT);
        req.setBookedHours(BigInteger.valueOf(3));

        // userId comes from Authentication (TEST_USER_ID) inside the controller
        ResponseEntity<Object> createResp = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/booked-work",
                req, Object.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<TaskParticipantResponse[]> list = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                TaskParticipantResponse[].class);

        assertThat(list.getBody()).anyMatch(p ->
                p.getRole() == TaskParticipantRole.CONTRIBUTOR
                && TestSecurityConfig.TEST_USER_ID.equals(p.getUserId()));
    }
}
