package com.demo.task;

import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.UserDto;
import com.demo.task.client.UserClient;
import com.demo.task.repository.TaskPhaseRepository;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskPhaseControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskPhaseRepository phaseRepository;

    @Autowired
    TaskProjectRepository projectRepository;

    @Autowired
    TaskRepository taskRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private UUID projectId;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();
        when(userClient.getUserById(ALICE_ID))
                .thenReturn(new UserDto(ALICE_ID, "Alice", "alice@demo.com", null, true, null));
        projectId = createProject("Default Project").getId();
    }

    // ── GET /api/v1/phases?projectId ─────────────────────────────────

    @Test
    void getPhasesForProject_whenEmpty_returnsEmptyList() {
        ResponseEntity<TaskPhaseResponse[]> response =
                restTemplate.getForEntity("/api/v1/phases?projectId=" + projectId, TaskPhaseResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getPhasesForProject_returnsAllCreatedPhases() {
        createPhase("Backlog", false);
        createPhase("In Review", false);

        ResponseEntity<TaskPhaseResponse[]> response =
                restTemplate.getForEntity("/api/v1/phases?projectId=" + projectId, TaskPhaseResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).extracting("name")
                .containsExactlyInAnyOrder("Backlog", "In Review");
    }

    // ── GET /api/v1/phases/{id} ──────────────────────────────────────

    @Test
    void getPhaseById_returnsPhase() {
        TaskPhaseResponse created = createPhase("Backlog", false);

        ResponseEntity<TaskPhaseResponse> response =
                restTemplate.getForEntity("/api/v1/phases/" + created.getId(), TaskPhaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Backlog");
        assertThat(response.getBody().getProjectId()).isEqualTo(projectId);
    }

    @Test
    void getPhaseById_whenNotFound_returns404() {
        assertThat(restTemplate.getForEntity("/api/v1/phases/" + UUID.randomUUID(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/v1/phases ──────────────────────────────────────────

    @Test
    void createPhase_persistsAndReturnsPhase() {
        ResponseEntity<TaskPhaseResponse> response =
                restTemplate.postForEntity("/api/v1/phases", phaseRequest("Sprint 1", true), TaskPhaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Sprint 1");
        assertThat(response.getBody().isDefault()).isTrue();
        assertThat(phaseRepository.count()).isEqualTo(1);
    }

    @Test
    void createTwoDefaultPhases_onlyLastIsDefault() {
        createPhase("Phase A", true);
        createPhase("Phase B", true);

        ResponseEntity<TaskPhaseResponse[]> phases =
                restTemplate.getForEntity("/api/v1/phases?projectId=" + projectId, TaskPhaseResponse[].class);

        long defaultCount = java.util.Arrays.stream(phases.getBody())
                .filter(TaskPhaseResponse::isDefault).count();
        assertThat(defaultCount).isEqualTo(1);
        assertThat(phases.getBody()).filteredOn(TaskPhaseResponse::isDefault)
                .extracting("name").containsExactly("Phase B");
    }

    // ── PUT /api/v1/phases/{id} ──────────────────────────────────────

    @Test
    void updatePhase_updatesFields() {
        TaskPhaseResponse created = createPhase("Old Name", false);

        ResponseEntity<TaskPhaseResponse> response = restTemplate.exchange(
                "/api/v1/phases/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(phaseRequest("New Name", true)),
                TaskPhaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("New Name");
        assertThat(response.getBody().isDefault()).isTrue();
    }

    @Test
    void updatePhase_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/phases/" + UUID.randomUUID(),
                HttpMethod.PUT,
                new HttpEntity<>(phaseRequest("X", false)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/v1/phases/{id} ───────────────────────────────────

    @Test
    void deletePhase_softDeletesPhase() {
        TaskPhaseResponse created = createPhase("To Delete", false);

        restTemplate.delete("/api/v1/phases/" + created.getId());

        assertThat(phaseRepository.count()).isEqualTo(0); // @SQLRestriction filters it
        assertThat(restTemplate.getForEntity("/api/v1/phases/" + created.getId(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletePhase_withActiveTasks_returns409() {
        TaskPhaseResponse phase = createPhase("Blocked Phase", false);
        createTask("Task", TaskStatus.TODO, ALICE_ID, phase.getId());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/phases/" + phase.getId(), HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deletePhase_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/phases/" + UUID.randomUUID(), HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Default phase assignment on task creation ─────────────────

    @Test
    void createTask_withNoPhaseId_assignsProjectDefaultPhase() {
        TaskPhaseResponse defaultPhase = createPhase("Default Phase", true);

        TaskRequest req = taskRequest(TaskStatus.TODO, ALICE_ID, null);
        ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getPhase()).isNotNull();
        assertThat(response.getBody().getPhase().getId()).isEqualTo(defaultPhase.getId());
    }

    @Test
    void createTask_withNoPhaseIdAndNoDefault_hasNullPhase() {
        TaskRequest req = taskRequest(TaskStatus.TODO, ALICE_ID, null);
        ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getPhase()).isNull();
    }

    @Test
    void createTask_withExplicitPhaseId_usesProvidedPhase() {
        createPhase("Default Phase", true);
        TaskPhaseResponse explicit = createPhase("Explicit Phase", false);

        TaskRequest req = taskRequest(TaskStatus.TODO, ALICE_ID, explicit.getId());
        ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getPhase().getId()).isEqualTo(explicit.getId());
    }

    @Test
    void createTask_withNonExistentPhaseId_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks", taskRequest(TaskStatus.TODO, ALICE_ID, UUID.randomUUID()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private TaskProjectResponse createProject(String name) {
        TaskProjectRequest req = new TaskProjectRequest();
        req.setName(name);
        return restTemplate.postForEntity("/api/v1/projects", req, TaskProjectResponse.class).getBody();
    }

    private TaskPhaseResponse createPhase(String name, boolean isDefault) {
        return restTemplate.postForEntity("/api/v1/phases", phaseRequest(name, isDefault), TaskPhaseResponse.class).getBody();
    }

    private TaskResponse createTask(String title, TaskStatus status, UUID userId, UUID phaseId) {
        return restTemplate.postForEntity("/api/v1/tasks", taskRequest(status, userId, phaseId), TaskResponse.class).getBody();
    }

    private TaskPhaseRequest phaseRequest(String name, boolean isDefault) {
        TaskPhaseRequest req = new TaskPhaseRequest();
        req.setName(name);
        req.setProjectId(projectId);
        req.setDefault(isDefault);
        return req;
    }

    private TaskRequest taskRequest(TaskStatus status, UUID userId, UUID phaseId) {
        TaskRequest req = new TaskRequest();
        req.setTitle("Task");
        req.setDescription("desc");
        req.setStatus(status);
        req.setAssignedUserId(userId);
        req.setProjectId(projectId);
        req.setPhaseId(phaseId);
        return req;
    }
}
