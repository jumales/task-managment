package com.demo.task;

import com.demo.common.dto.TaskPhaseName;
import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskPlannedWorkRequest;
import com.demo.common.dto.TaskPlannedWorkResponse;
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
 * Integration tests for {@link com.demo.task.controller.TaskPlannedWorkController}.
 */
@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskPlannedWorkControllerIT {

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

    private String taskId;
    private UUID projectId;
    private UUID phaseId;

    @BeforeEach
    void setUp() {
        plannedWorkRepository.deleteAll();
        bookedWorkRepository.deleteAll();
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();

        UserDto alice     = new UserDto(ALICE_ID, "Alice Johnson", "alice@demo.com", null, true, null, "en");
        UserDto testAdmin = new UserDto(TestSecurityConfig.TEST_USER_ID, "Test Admin", "admin@test.com", null, true, null, "en");

        when(userClient.getUserById(ALICE_ID)).thenReturn(alice);
        when(userClient.getUserById(TestSecurityConfig.TEST_USER_ID)).thenReturn(testAdmin);
        when(userClient.getUsersByIds(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return List.of(alice, testAdmin).stream().filter(u -> ids.contains(u.getId())).toList();
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
    }

    // ── GET /api/v1/tasks/{taskId}/planned-work ──────────────────────────────

    @Test
    void getPlannedWork_whenNoneExist_returnsEmptyList() {
        ResponseEntity<TaskPlannedWorkResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/planned-work",
                TaskPlannedWorkResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getPlannedWork_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/planned-work",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/v1/tasks/{taskId}/planned-work ─────────────────────────────

    @Test
    void createPlannedWork_persistsAndReturnsEntry() {
        ResponseEntity<TaskPlannedWorkResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/planned-work",
                plannedWorkRequest(WorkType.DEVELOPMENT, "8"),
                TaskPlannedWorkResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        // creator is resolved from the authenticated test user, not from the request body
        assertThat(response.getBody().getUserId()).isEqualTo(TestSecurityConfig.TEST_USER_ID);
        assertThat(response.getBody().getUserName()).isEqualTo("Test Admin");
        assertThat(response.getBody().getWorkType()).isEqualTo(WorkType.DEVELOPMENT);
        assertThat(response.getBody().getPlannedHours()).isEqualTo(BigInteger.valueOf(8));
    }

    @Test
    void createPlannedWork_differentWorkTypes_areAllReturned() {
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/planned-work",
                plannedWorkRequest(WorkType.DEVELOPMENT, "4"), TaskPlannedWorkResponse.class);
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/planned-work",
                plannedWorkRequest(WorkType.TESTING, "2"), TaskPlannedWorkResponse.class);

        ResponseEntity<TaskPlannedWorkResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/planned-work",
                TaskPlannedWorkResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void createPlannedWork_duplicateWorkType_returns409() {
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/planned-work",
                plannedWorkRequest(WorkType.DEVELOPMENT, "4"), TaskPlannedWorkResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/planned-work",
                plannedWorkRequest(WorkType.DEVELOPMENT, "8"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createPlannedWork_whenTaskNotInTodoStatus_returns400() {
        // Move task to IN_PROGRESS
        TaskRequest updateReq = new TaskRequest();
        updateReq.setTitle("Sample Task");
        updateReq.setStatus(TaskStatus.IN_PROGRESS);
        updateReq.setType(TaskType.FEATURE);
        updateReq.setAssignedUserId(ALICE_ID);
        updateReq.setProjectId(projectId);
        updateReq.setPhaseId(phaseId);
        restTemplate.exchange("/api/v1/tasks/" + taskId, HttpMethod.PUT,
                new HttpEntity<>(updateReq), TaskResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/planned-work",
                plannedWorkRequest(WorkType.DEVELOPMENT, "8"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPlannedWork_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/planned-work",
                plannedWorkRequest(WorkType.PLANNING, "1"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private TaskPlannedWorkRequest plannedWorkRequest(WorkType workType, String plannedHours) {
        TaskPlannedWorkRequest req = new TaskPlannedWorkRequest();
        req.setWorkType(workType);
        req.setPlannedHours(new BigInteger(plannedHours));
        return req;
    }
}
