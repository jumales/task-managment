package com.demo.task;

import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskTimelineRequest;
import com.demo.common.dto.TaskTimelineResponse;
import com.demo.common.dto.TimelineState;
import com.demo.common.dto.UserDto;
import com.demo.task.client.UserClient;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link com.demo.task.controller.TaskTimelineController}.
 */
@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskTimelineControllerIT {

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
    TaskTimelineRepository timelineRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();

    private String taskId;

    @BeforeEach
    void setUp() {
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        UserDto alice     = new UserDto(ALICE_ID, "Alice Johnson", "alice@demo.com", null, true, null, null, "en");
        UserDto testAdmin = new UserDto(TestSecurityConfig.TEST_USER_ID, "Test Admin", "admin@test.com", null, true, null, null, "en");

        when(userClient.getUserById(ALICE_ID)).thenReturn(alice);
        when(userClient.getUserById(TestSecurityConfig.TEST_USER_ID)).thenReturn(testAdmin);
        when(userClient.getUsersByIds(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return List.of(alice, testAdmin).stream().filter(u -> ids.contains(u.getId())).toList();
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
        taskId = restTemplate.postForEntity("/api/v1/tasks", taskReq, TaskResponse.class)
                .getBody().getId().toString();
    }

    // ── GET /api/v1/tasks/{taskId}/timelines ────────────────────────────────

    @Test
    void getTimelines_whenNoneExist_returnsEmptyList() {
        ResponseEntity<TaskTimelineResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/timelines",
                TaskTimelineResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getTimelines_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/timelines",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── PUT /api/v1/tasks/{taskId}/timelines/{state} ─────────────────────────

    @Test
    void setState_createsNewEntry() {
        TaskTimelineRequest request = timelineRequest(ALICE_ID, Instant.parse("2026-04-01T08:00:00Z"));

        ResponseEntity<TaskTimelineResponse> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/timelines/PLANNED_START",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                TaskTimelineResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getState()).isEqualTo(TimelineState.PLANNED_START);
        assertThat(response.getBody().getTimestamp()).isEqualTo(Instant.parse("2026-04-01T08:00:00Z"));
        assertThat(response.getBody().getSetByUserId()).isEqualTo(ALICE_ID);
        assertThat(response.getBody().getSetByUserName()).isEqualTo("Alice Johnson");
    }

    @Test
    void setState_updatesExistingEntry() {
        TaskTimelineRequest initial = timelineRequest(ALICE_ID, Instant.parse("2026-04-01T08:00:00Z"));
        restTemplate.exchange("/api/v1/tasks/" + taskId + "/timelines/PLANNED_END",
                HttpMethod.PUT, new HttpEntity<>(initial), TaskTimelineResponse.class);

        TaskTimelineRequest updated = timelineRequest(ALICE_ID, Instant.parse("2026-04-15T17:00:00Z"));
        ResponseEntity<TaskTimelineResponse> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/timelines/PLANNED_END",
                HttpMethod.PUT,
                new HttpEntity<>(updated),
                TaskTimelineResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTimestamp()).isEqualTo(Instant.parse("2026-04-15T17:00:00Z"));
        // Only one active entry should exist for this state
        assertThat(timelineRepository.findByTaskIdOrderByStateAsc(UUID.fromString(taskId))).hasSize(1);
    }

    @Test
    void setState_allFourStates_areReturnedInList() {
        Instant base = Instant.parse("2026-04-01T00:00:00Z");
        for (TimelineState state : TimelineState.values()) {
            restTemplate.exchange("/api/v1/tasks/" + taskId + "/timelines/" + state,
                    HttpMethod.PUT, new HttpEntity<>(timelineRequest(ALICE_ID, base)), TaskTimelineResponse.class);
        }

        ResponseEntity<TaskTimelineResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/timelines",
                TaskTimelineResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(4);
    }

    @Test
    void setState_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + UUID.randomUUID() + "/timelines/REAL_START",
                HttpMethod.PUT,
                new HttpEntity<>(timelineRequest(ALICE_ID, Instant.now())),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/v1/tasks/{taskId}/timelines/{state} ──────────────────────

    @Test
    void deleteState_removesItFromList() {
        restTemplate.exchange("/api/v1/tasks/" + taskId + "/timelines/REAL_END",
                HttpMethod.PUT, new HttpEntity<>(timelineRequest(ALICE_ID, Instant.now())),
                TaskTimelineResponse.class);

        restTemplate.exchange("/api/v1/tasks/" + taskId + "/timelines/REAL_END",
                HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);

        ResponseEntity<TaskTimelineResponse[]> listResponse = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/timelines",
                TaskTimelineResponse[].class);
        assertThat(listResponse.getBody()).noneMatch(e -> e.getState() == TimelineState.REAL_END);
    }

    @Test
    void deleteState_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/timelines/REAL_START",
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private TaskTimelineRequest timelineRequest(UUID setByUserId, Instant timestamp) {
        TaskTimelineRequest req = new TaskTimelineRequest();
        req.setSetByUserId(setByUserId);
        req.setTimestamp(timestamp);
        return req;
    }
}
