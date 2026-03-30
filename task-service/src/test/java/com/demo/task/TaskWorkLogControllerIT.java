package com.demo.task;

import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskWorkLogRequest;
import com.demo.common.dto.TaskWorkLogResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.dto.WorkType;
import com.demo.task.client.UserClient;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskTimelineRepository;
import com.demo.task.repository.TaskWorkLogRepository;
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
 * Integration tests for {@link com.demo.task.controller.TaskWorkLogController}.
 */
@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskWorkLogControllerIT {

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
    TaskWorkLogRepository workLogRepository;

    @Autowired
    TaskTimelineRepository timelineRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID   = UUID.randomUUID();

    private String taskId;

    @BeforeEach
    void setUp() {
        workLogRepository.deleteAll();
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
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
        UUID projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();

        TaskRequest taskReq = new TaskRequest();
        taskReq.setTitle("Sample Task");
        taskReq.setStatus(TaskStatus.TODO);
        taskReq.setAssignedUserId(ALICE_ID);
        taskReq.setProjectId(projectId);
        taskReq.setPlannedStart(Instant.parse("2026-04-01T08:00:00Z"));
        taskReq.setPlannedEnd(Instant.parse("2026-04-30T17:00:00Z"));
        taskId = restTemplate.postForEntity("/api/v1/tasks", taskReq, TaskResponse.class)
                .getBody().getId().toString();
    }

    // ── GET /api/v1/tasks/{taskId}/work-logs ────────────────────────────────

    @Test
    void getWorkLogs_whenNoneExist_returnsEmptyList() {
        ResponseEntity<TaskWorkLogResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/work-logs",
                TaskWorkLogResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getWorkLogs_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/work-logs",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/v1/tasks/{taskId}/work-logs ───────────────────────────────

    @Test
    void createWorkLog_persistsAndReturnsEntry() {
        TaskWorkLogRequest request = workLogRequest(ALICE_ID, WorkType.DEVELOPMENT, "8", "5");

        ResponseEntity<TaskWorkLogResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/work-logs",
                request,
                TaskWorkLogResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getUserId()).isEqualTo(ALICE_ID);
        assertThat(response.getBody().getUserName()).isEqualTo("Alice Johnson");
        assertThat(response.getBody().getWorkType()).isEqualTo(WorkType.DEVELOPMENT);
        assertThat(response.getBody().getPlannedHours()).isEqualTo(BigInteger.valueOf(8));
        assertThat(response.getBody().getBookedHours()).isEqualTo(BigInteger.valueOf(5));
    }

    @Test
    void createWorkLog_multipleEntriesForSameTask_areAllReturned() {
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/work-logs",
                workLogRequest(ALICE_ID, WorkType.DEVELOPMENT, "4", "4"), TaskWorkLogResponse.class);
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/work-logs",
                workLogRequest(BOB_ID, WorkType.TESTING, "2", "1"), TaskWorkLogResponse.class);

        ResponseEntity<TaskWorkLogResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/work-logs",
                TaskWorkLogResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void createWorkLog_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/work-logs",
                workLogRequest(ALICE_ID, WorkType.PLANNING, "1", "0"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── PUT /api/v1/tasks/{taskId}/work-logs/{workLogId} ────────────────────

    @Test
    void updateWorkLog_updatesAllFields() {
        String workLogId = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/work-logs",
                workLogRequest(ALICE_ID, WorkType.DEVELOPMENT, "4", "0"),
                TaskWorkLogResponse.class).getBody().getId().toString();

        TaskWorkLogRequest update = workLogRequest(BOB_ID, WorkType.CODE_REVIEW, "2", "2");

        ResponseEntity<TaskWorkLogResponse> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/work-logs/" + workLogId,
                HttpMethod.PUT,
                new HttpEntity<>(update),
                TaskWorkLogResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUserId()).isEqualTo(BOB_ID);
        assertThat(response.getBody().getWorkType()).isEqualTo(WorkType.CODE_REVIEW);
        assertThat(response.getBody().getPlannedHours()).isEqualTo(BigInteger.valueOf(4)); // immutable: stays at original 4
        assertThat(response.getBody().getBookedHours()).isEqualTo(BigInteger.valueOf(2));
    }

    @Test
    void updateWorkLog_plannedHoursAreImmutable() {
        // Create a log with plannedHours = 10
        String workLogId = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/work-logs",
                workLogRequest(ALICE_ID, WorkType.DEVELOPMENT, "10", "0"),
                TaskWorkLogResponse.class).getBody().getId().toString();

        // Update with a different plannedHours value — should be ignored
        TaskWorkLogRequest update = workLogRequest(ALICE_ID, WorkType.DEVELOPMENT, "99", "5");

        ResponseEntity<TaskWorkLogResponse> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/work-logs/" + workLogId,
                HttpMethod.PUT,
                new HttpEntity<>(update),
                TaskWorkLogResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPlannedHours()).isEqualTo(BigInteger.valueOf(10)); // unchanged
        assertThat(response.getBody().getBookedHours()).isEqualTo(BigInteger.valueOf(5));   // updated
    }

    @Test
    void updateWorkLog_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/work-logs/" + UUID.randomUUID(),
                HttpMethod.PUT,
                new HttpEntity<>(workLogRequest(ALICE_ID, WorkType.TESTING, "1", "0")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/v1/tasks/{taskId}/work-logs/{workLogId} ─────────────────

    @Test
    void deleteWorkLog_removesItFromList() {
        String workLogId = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/work-logs",
                workLogRequest(ALICE_ID, WorkType.MEETING, "1", "1"),
                TaskWorkLogResponse.class).getBody().getId().toString();

        restTemplate.delete("/api/v1/tasks/" + taskId + "/work-logs/" + workLogId);

        ResponseEntity<TaskWorkLogResponse[]> listResponse = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/work-logs",
                TaskWorkLogResponse[].class);
        assertThat(listResponse.getBody()).noneMatch(l -> l.getId().toString().equals(workLogId));
    }

    @Test
    void deleteWorkLog_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/work-logs/" + UUID.randomUUID(),
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private TaskWorkLogRequest workLogRequest(UUID userId, WorkType workType,
                                              String plannedHours, String bookedHours) {
        TaskWorkLogRequest req = new TaskWorkLogRequest();
        req.setUserId(userId);
        req.setWorkType(workType);
        req.setPlannedHours(new BigInteger(plannedHours));
        req.setBookedHours(new BigInteger(bookedHours));
        return req;
    }
}
