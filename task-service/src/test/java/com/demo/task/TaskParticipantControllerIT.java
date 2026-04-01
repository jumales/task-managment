package com.demo.task;

import com.demo.common.dto.TaskParticipantRequest;
import com.demo.common.dto.TaskParticipantResponse;
import com.demo.common.dto.TaskParticipantRole;
import com.demo.common.dto.TaskPhaseName;
import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.UserDto;
import com.demo.task.client.UserClient;
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
import org.springframework.http.*;
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
 * Integration tests for {@link com.demo.task.controller.TaskParticipantController}.
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
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();

        alice = new UserDto(ALICE_ID, "Alice Johnson", "alice@demo.com", null, true, null, null, "en");
        bob   = new UserDto(BOB_ID,   "Bob Smith",     "bob@demo.com",   null, true, null, null, "en");
        UserDto testAdmin = new UserDto(TestSecurityConfig.TEST_USER_ID, "Test Admin", "admin@test.com", null, true, null, null, "en");

        when(userClient.getUserById(ALICE_ID)).thenReturn(alice);
        when(userClient.getUserById(BOB_ID)).thenReturn(bob);
        when(userClient.getUserById(TestSecurityConfig.TEST_USER_ID)).thenReturn(testAdmin);
        when(userClient.getUsersByIds(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return List.of(alice, bob, testAdmin).stream().filter(u -> ids.contains(u.getId())).toList();
        });

        // Create a project then a task to use across tests
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
        restTemplate.exchange("/api/v1/projects/" + projectId, org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(defaultPhaseProjectReq), TaskProjectResponse.class);

        TaskRequest taskReq = new TaskRequest();
        taskReq.setTitle("Sample Task");
        taskReq.setDescription("desc");
        taskReq.setStatus(TaskStatus.TODO);
        taskReq.setAssignedUserId(ALICE_ID);
        taskReq.setProjectId(projectId);
        taskReq.setPhaseId(phaseId);
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
                p.getRole() == TaskParticipantRole.CREATOR && TestSecurityConfig.TEST_USER_ID.equals(p.getUserId()));
        assertThat(response.getBody()).anyMatch(p ->
                p.getRole() == TaskParticipantRole.ASSIGNEE && ALICE_ID.equals(p.getUserId()));
    }

    // ── POST /api/v1/tasks/{taskId}/participants ─────────────────────────────

    @Test
    void addParticipant_createsParticipantWithRole() {
        TaskParticipantRequest request = new TaskParticipantRequest();
        request.setUserId(BOB_ID);
        request.setRole(TaskParticipantRole.REVIEWER);

        ResponseEntity<TaskParticipantResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                request,
                TaskParticipantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getUserId()).isEqualTo(BOB_ID);
        assertThat(response.getBody().getUserName()).isEqualTo("Bob Smith");
        assertThat(response.getBody().getRole()).isEqualTo(TaskParticipantRole.REVIEWER);
    }

    @Test
    void addParticipant_withCreatorRole_returns400() {
        TaskParticipantRequest request = new TaskParticipantRequest();
        request.setUserId(BOB_ID);
        request.setRole(TaskParticipantRole.CREATOR);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                request,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void addParticipant_duplicate_returns409() {
        // ASSIGNEE for Alice was auto-created; try adding her as ASSIGNEE again
        TaskParticipantRequest request = new TaskParticipantRequest();
        request.setUserId(ALICE_ID);
        request.setRole(TaskParticipantRole.ASSIGNEE);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                request,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void addParticipant_sameUserDifferentRole_succeeds() {
        // Alice is already ASSIGNEE; add her as VIEWER — should succeed
        TaskParticipantRequest request = new TaskParticipantRequest();
        request.setUserId(ALICE_ID);
        request.setRole(TaskParticipantRole.VIEWER);

        ResponseEntity<TaskParticipantResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                request,
                TaskParticipantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ── DELETE /api/v1/tasks/{taskId}/participants/{participantId} ───────────

    @Test
    void removeParticipant_removesItFromList() {
        // Add Bob as REVIEWER, then remove him
        TaskParticipantRequest request = new TaskParticipantRequest();
        request.setUserId(BOB_ID);
        request.setRole(TaskParticipantRole.REVIEWER);
        String participantId = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                request,
                TaskParticipantResponse.class).getBody().getId().toString();

        restTemplate.delete("/api/v1/tasks/" + taskId + "/participants/" + participantId);

        ResponseEntity<TaskParticipantResponse[]> listResponse = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/participants",
                TaskParticipantResponse[].class);
        assertThat(listResponse.getBody()).noneMatch(p -> p.getId().toString().equals(participantId));
    }

    @Test
    void removeParticipant_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/participants/" + UUID.randomUUID(),
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
