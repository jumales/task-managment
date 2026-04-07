package com.demo.task;

import com.demo.common.dto.TaskBookedWorkRequest;
import com.demo.common.dto.TaskBookedWorkResponse;
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
import com.demo.task.repository.TaskBookedWorkRepository;
import com.demo.task.repository.TaskPhaseRepository;
import com.demo.task.repository.TaskPlannedWorkRepository;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskTimelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
 * Integration tests for {@link com.demo.task.controller.TaskBookedWorkController}.
 */
@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskBookedWorkControllerIT {

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
    TaskPlannedWorkRepository plannedWorkRepository;

    @Autowired
    TaskBookedWorkRepository bookedWorkRepository;

    @Autowired
    TaskTimelineRepository timelineRepository;

    @Autowired
    TaskPhaseRepository phaseRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID   = UUID.randomUUID();

    private String taskId;
    private UUID projectId;
    private UUID phaseId;

    @BeforeEach
    void setUp() {
        bookedWorkRepository.deleteAll();
        plannedWorkRepository.deleteAll();
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();

        UserDto alice     = new UserDto(ALICE_ID, "Alice Johnson", "alice@demo.com", null, true, null, null, "en");
        UserDto bob       = new UserDto(BOB_ID,   "Bob Smith",     "bob@demo.com",   null, true, null, null, "en");
        UserDto testAdmin = new UserDto(TestSecurityConfig.TEST_USER_ID, "Test Admin", "admin@test.com", null, true, null, null, "en");

        when(userClient.getUserById(ALICE_ID)).thenReturn(alice);
        when(userClient.getUserById(BOB_ID)).thenReturn(bob);
        when(userClient.getUserById(TestSecurityConfig.TEST_USER_ID)).thenReturn(testAdmin);
        when(userClient.getUsersByIds(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return List.of(alice, bob, testAdmin).stream().filter(u -> ids.contains(u.getId())).toList();
        });

        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Test Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();

        TaskPhaseRequest phaseReq = new TaskPhaseRequest();
        phaseReq.setName(TaskPhaseName.BACKLOG);
        phaseReq.setProjectId(projectId);
        phaseId = restTemplate.postForEntity("/api/v1/phases", phaseReq, TaskPhaseResponse.class).getBody().getId();
        TaskProjectRequest defaultPhaseProjectReq = new TaskProjectRequest();
        defaultPhaseProjectReq.setName("Test Project");
        defaultPhaseProjectReq.setDefaultPhaseId(phaseId);
        restTemplate.exchange("/api/v1/projects/" + projectId, org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(defaultPhaseProjectReq), TaskProjectResponse.class);

        TaskRequest taskReq = new TaskRequest();
        taskReq.setTitle("Sample Task");
        taskReq.setStatus(TaskStatus.TODO);
        taskReq.setAssignedUserId(ALICE_ID);
        taskReq.setProjectId(projectId);
        taskReq.setPhaseId(phaseId);
        taskReq.setType(TaskType.FEATURE);
        taskReq.setPlannedStart(Instant.parse("2026-04-01T08:00:00Z"));
        taskReq.setPlannedEnd(Instant.parse("2026-04-30T17:00:00Z"));
        taskId = restTemplate.postForEntity("/api/v1/tasks", taskReq, TaskResponse.class)
                .getBody().getId().toString();

        // Tasks always start in PLANNING; move to BACKLOG so booked-work operations are allowed in all other tests.
        TaskPhaseUpdateRequest phaseUpdateReq = new TaskPhaseUpdateRequest();
        phaseUpdateReq.setPhaseId(phaseId);
        restTemplate.exchange("/api/v1/tasks/" + taskId + "/phase", HttpMethod.PATCH,
                new HttpEntity<>(phaseUpdateReq), TaskResponse.class);
    }

    // ── GET /api/v1/tasks/{taskId}/booked-work ───────────────────────────────

    @Test
    void getBookedWork_whenNoneExist_returnsEmptyList() {
        ResponseEntity<TaskBookedWorkResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/booked-work",
                TaskBookedWorkResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getBookedWork_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/booked-work",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/v1/tasks/{taskId}/booked-work ──────────────────────────────

    @Test
    void createBookedWork_persistsAndReturnsEntry() {
        TaskBookedWorkRequest request = bookedWorkRequest(ALICE_ID, WorkType.DEVELOPMENT, "5");

        ResponseEntity<TaskBookedWorkResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/booked-work",
                request,
                TaskBookedWorkResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getUserId()).isEqualTo(ALICE_ID);
        assertThat(response.getBody().getUserName()).isEqualTo("Alice Johnson");
        assertThat(response.getBody().getWorkType()).isEqualTo(WorkType.DEVELOPMENT);
        assertThat(response.getBody().getBookedHours()).isEqualTo(BigInteger.valueOf(5));
    }

    @Test
    void createBookedWork_multipleEntriesForSameWorkType_areAllReturned() {
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/booked-work",
                bookedWorkRequest(ALICE_ID, WorkType.DEVELOPMENT, "3"), TaskBookedWorkResponse.class);
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/booked-work",
                bookedWorkRequest(BOB_ID, WorkType.DEVELOPMENT, "4"), TaskBookedWorkResponse.class);
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/booked-work",
                bookedWorkRequest(ALICE_ID, WorkType.TESTING, "2"), TaskBookedWorkResponse.class);

        ResponseEntity<TaskBookedWorkResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/booked-work",
                TaskBookedWorkResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(3);
    }

    @Test
    void createBookedWork_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/booked-work",
                bookedWorkRequest(ALICE_ID, WorkType.PLANNING, "1"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── PUT /api/v1/tasks/{taskId}/booked-work/{bookedWorkId} ────────────────

    @Test
    void updateBookedWork_updatesAllFields() {
        String bookedWorkId = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/booked-work",
                bookedWorkRequest(ALICE_ID, WorkType.DEVELOPMENT, "3"),
                TaskBookedWorkResponse.class).getBody().getId().toString();

        TaskBookedWorkRequest update = bookedWorkRequest(BOB_ID, WorkType.CODE_REVIEW, "6");

        ResponseEntity<TaskBookedWorkResponse> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/booked-work/" + bookedWorkId,
                HttpMethod.PUT,
                new HttpEntity<>(update),
                TaskBookedWorkResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUserId()).isEqualTo(BOB_ID);
        assertThat(response.getBody().getWorkType()).isEqualTo(WorkType.CODE_REVIEW);
        assertThat(response.getBody().getBookedHours()).isEqualTo(BigInteger.valueOf(6));
    }

    @Test
    void updateBookedWork_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/booked-work/" + UUID.randomUUID(),
                HttpMethod.PUT,
                new HttpEntity<>(bookedWorkRequest(ALICE_ID, WorkType.TESTING, "1")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/v1/tasks/{taskId}/booked-work/{bookedWorkId} ────────────

    @Test
    void deleteBookedWork_removesItFromList() {
        String bookedWorkId = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/booked-work",
                bookedWorkRequest(ALICE_ID, WorkType.MEETING, "1"),
                TaskBookedWorkResponse.class).getBody().getId().toString();

        restTemplate.delete("/api/v1/tasks/" + taskId + "/booked-work/" + bookedWorkId);

        ResponseEntity<TaskBookedWorkResponse[]> listResponse = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/booked-work",
                TaskBookedWorkResponse[].class);
        assertThat(listResponse.getBody()).noneMatch(e -> e.getId().toString().equals(bookedWorkId));
    }

    @Test
    void deleteBookedWork_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/booked-work/" + UUID.randomUUID(),
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── PLANNING phase guard ─────────────────────────────────────────────────

    @Test
    void createBookedWork_whenTaskInPlanningPhase_returns422() {
        // A freshly created task is always in PLANNING phase
        String planningTaskId = createTask("Planning Task");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + planningTaskId + "/booked-work",
                bookedWorkRequest(ALICE_ID, WorkType.DEVELOPMENT, "3"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void updateBookedWork_whenTaskInPlanningPhase_returns422() {
        // validateNotPlanningPhase is checked before the booked-work entry is looked up,
        // so a random ID is enough to trigger the 422 before any 404.
        String planningTaskId = createTask("Planning Task 2");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + planningTaskId + "/booked-work/" + UUID.randomUUID(),
                HttpMethod.PUT,
                new HttpEntity<>(bookedWorkRequest(ALICE_ID, WorkType.DEVELOPMENT, "3")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /** Creates a task under the test project. The task starts in PLANNING phase. */
    private String createTask(String title) {
        TaskRequest req = new TaskRequest();
        req.setTitle(title);
        req.setStatus(TaskStatus.TODO);
        req.setAssignedUserId(ALICE_ID);
        req.setProjectId(projectId);
        req.setPhaseId(phaseId);
        req.setType(TaskType.FEATURE);
        req.setPlannedStart(Instant.parse("2026-04-01T08:00:00Z"));
        req.setPlannedEnd(Instant.parse("2026-04-30T17:00:00Z"));
        return restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class)
                .getBody().getId().toString();
    }

    private TaskBookedWorkRequest bookedWorkRequest(UUID userId, WorkType workType, String bookedHours) {
        TaskBookedWorkRequest req = new TaskBookedWorkRequest();
        req.setUserId(userId);
        req.setWorkType(workType);
        req.setBookedHours(new BigInteger(bookedHours));
        return req;
    }
}
