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
import com.demo.task.client.UserClient;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskPhaseControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskPhaseRepository phaseRepository;

    @Autowired
    TaskProjectRepository projectRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskTimelineRepository timelineRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private UUID projectId;
    private UUID planningPhaseId;

    @BeforeEach
    void setUp() {
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();
        when(userClient.getUserById(ALICE_ID))
                .thenReturn(new UserDto(ALICE_ID, "Alice", "alice@demo.com", null, true, null, null, "en"));
        projectId = createProject("Default Project").getId();
        // Wait for async createDefaultPhasesForProject to complete and get the auto-created PLANNING phase.
        planningPhaseId = waitForPhase(projectId, TaskPhaseName.PLANNING);
    }

    // ── GET /api/v1/phases?projectId ─────────────────────────────────

    @Test
    void getPhasesForProject_afterProjectCreation_returnsAllSevenPhases() {
        ResponseEntity<TaskPhaseResponse[]> response =
                restTemplate.getForEntity("/api/v1/phases?projectId=" + projectId, TaskPhaseResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(TaskPhaseName.values().length);
        assertThat(response.getBody()).extracting("name")
                .containsExactlyInAnyOrder(TaskPhaseName.values());
        assertThat(response.getBody()).allSatisfy(p -> assertThat(p.getCustomName()).isNull());
    }

    @Test
    void getPhasesForProject_additionalPhaseCreated_appearsInList() {
        // 7 phases auto-created on project creation; adding one more yields 8
        createPhase(TaskPhaseName.BACKLOG);

        ResponseEntity<TaskPhaseResponse[]> response =
                restTemplate.getForEntity("/api/v1/phases?projectId=" + projectId, TaskPhaseResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(TaskPhaseName.values().length + 1);
    }

    // ── GET /api/v1/phases/{id} ──────────────────────────────────────

    @Test
    void getPhaseById_returnsPhase() {
        TaskPhaseResponse created = createPhase(TaskPhaseName.BACKLOG);

        ResponseEntity<TaskPhaseResponse> response =
                restTemplate.getForEntity("/api/v1/phases/" + created.getId(), TaskPhaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo(TaskPhaseName.BACKLOG);
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
        // 7 phases are auto-created when the project is created; adding one more yields 8
        ResponseEntity<TaskPhaseResponse> response =
                restTemplate.postForEntity("/api/v1/phases", phaseRequest(TaskPhaseName.IN_PROGRESS), TaskPhaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(TaskPhaseName.IN_PROGRESS);
        assertThat(phaseRepository.count()).isEqualTo(TaskPhaseName.values().length + 1);
    }

    // ── PUT /api/v1/phases/{id} ──────────────────────────────────────

    @Test
    void updatePhase_updatesFields() {
        TaskPhaseResponse created = createPhase(TaskPhaseName.BACKLOG);

        ResponseEntity<TaskPhaseResponse> response = restTemplate.exchange(
                "/api/v1/phases/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(phaseRequest(TaskPhaseName.DONE)),
                TaskPhaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo(TaskPhaseName.DONE);
    }

    @Test
    void updatePhase_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/phases/" + UUID.randomUUID(),
                HttpMethod.PUT,
                new HttpEntity<>(phaseRequest(TaskPhaseName.TODO)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/v1/phases/{id} ───────────────────────────────────

    @Test
    void deletePhase_softDeletesPhase() {
        // 7 auto-created + 1 manually created = 8; after deleting the manual one, 7 remain
        TaskPhaseResponse created = createPhase(TaskPhaseName.BACKLOG);

        restTemplate.delete("/api/v1/phases/" + created.getId());

        assertThat(phaseRepository.count()).isEqualTo(TaskPhaseName.values().length); // @SQLRestriction filters deleted row
        assertThat(restTemplate.getForEntity("/api/v1/phases/" + created.getId(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletePhase_withActiveTasks_returns409() {
        // Tasks always start in PLANNING; deleting the PLANNING phase while a task is in it must be blocked.
        createTask("Task", TaskStatus.TODO, ALICE_ID, null);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/phases/" + planningPhaseId, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

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
    void createTask_alwaysStartsInPlanningPhase() {
        // phaseId in request is ignored — tasks always start in the PLANNING phase
        TaskRequest req = taskRequest(TaskStatus.TODO, ALICE_ID, null);
        ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getPhase()).isNotNull();
        assertThat(response.getBody().getPhase().getName()).isEqualTo(TaskPhaseName.PLANNING);
    }

    @Test
    void createTask_explicitPhaseIdIsIgnored_taskStartsInPlanning() {
        // Providing an explicit phaseId has no effect; task still lands in PLANNING
        TaskPhaseResponse explicit = createPhase(TaskPhaseName.IN_REVIEW);

        TaskRequest req = taskRequest(TaskStatus.TODO, ALICE_ID, explicit.getId());
        ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getPhase().getName()).isEqualTo(TaskPhaseName.PLANNING);
    }

    @Test
    void createTask_withNonExistentPhaseId_stillStartsInPlanning() {
        // Non-existent phaseId is silently ignored; task is assigned PLANNING phase
        TaskRequest req = taskRequest(TaskStatus.TODO, ALICE_ID, UUID.randomUUID());
        ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/api/v1/tasks", req, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getPhase().getName()).isEqualTo(TaskPhaseName.PLANNING);
    }

    // ── Project default phase ─────────────────────────────────────

    @Test
    void setProjectDefaultPhase_phaseMustBelongToProject() {
        TaskProjectResponse otherProject = createProject("Other Project");
        TaskPhaseResponse otherPhase = createPhaseForProject(TaskPhaseName.BACKLOG, otherProject.getId());

        // Attempt to set a phase from another project as the default — should fail with 400
        TaskProjectRequest req = new TaskProjectRequest();
        req.setName("Default Project");
        req.setDefaultPhaseId(otherPhase.getId());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId,
                HttpMethod.PUT,
                new HttpEntity<>(req),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void setProjectDefaultPhase_updatesProject() {
        TaskPhaseResponse phase = createPhase(TaskPhaseName.IN_PROGRESS);
        setProjectDefaultPhase(projectId, phase.getId());

        ResponseEntity<TaskProjectResponse> response =
                restTemplate.getForEntity("/api/v1/projects/" + projectId, TaskProjectResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getDefaultPhaseId()).isEqualTo(phase.getId());
    }

    // ── customName ───────────────────────────────────────────────

    @Test
    void getPhaseById_customNameIsNullByDefault() {
        TaskPhaseResponse[] phases = restTemplate
                .getForEntity("/api/v1/phases?projectId=" + projectId, TaskPhaseResponse[].class).getBody();
        UUID phaseId = phases[0].getId();

        ResponseEntity<TaskPhaseResponse> response =
                restTemplate.getForEntity("/api/v1/phases/" + phaseId, TaskPhaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCustomName()).isNull();
    }

    @Test
    void updatePhase_setsCustomName() {
        TaskPhaseResponse[] phases = restTemplate
                .getForEntity("/api/v1/phases?projectId=" + projectId, TaskPhaseResponse[].class).getBody();
        TaskPhaseResponse phase = phases[0];

        TaskPhaseRequest req = phaseRequest(phase.getName());
        req.setCustomName("My Custom Label");

        ResponseEntity<TaskPhaseResponse> response = restTemplate.exchange(
                "/api/v1/phases/" + phase.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(req),
                TaskPhaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCustomName()).isEqualTo("My Custom Label");
    }

    @Test
    void updatePhase_clearsCustomName_whenSetToNull() {
        TaskPhaseResponse[] phases = restTemplate
                .getForEntity("/api/v1/phases?projectId=" + projectId, TaskPhaseResponse[].class).getBody();
        TaskPhaseResponse phase = phases[0];

        // Set a custom name first
        TaskPhaseRequest setReq = phaseRequest(phase.getName());
        setReq.setCustomName("Temporary Label");
        restTemplate.exchange("/api/v1/phases/" + phase.getId(), HttpMethod.PUT, new HttpEntity<>(setReq), TaskPhaseResponse.class);

        // Clear it
        TaskPhaseRequest clearReq = phaseRequest(phase.getName());
        clearReq.setCustomName(null);

        ResponseEntity<TaskPhaseResponse> response = restTemplate.exchange(
                "/api/v1/phases/" + phase.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(clearReq),
                TaskPhaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getCustomName()).isNull();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private TaskProjectResponse createProject(String name) {
        TaskProjectRequest req = new TaskProjectRequest();
        req.setName(name);
        return restTemplate.postForEntity("/api/v1/projects", req, TaskProjectResponse.class).getBody();
    }

    private void setProjectDefaultPhase(UUID projectId, UUID phaseId) {
        TaskProjectRequest req = new TaskProjectRequest();
        req.setName("Default Project");
        req.setDefaultPhaseId(phaseId);
        restTemplate.exchange("/api/v1/projects/" + projectId, HttpMethod.PUT,
                new HttpEntity<>(req), TaskProjectResponse.class);
    }

    private TaskPhaseResponse createPhase(TaskPhaseName name) {
        return createPhaseForProject(name, projectId);
    }

    private TaskPhaseResponse createPhaseForProject(TaskPhaseName name, UUID forProjectId) {
        return restTemplate.postForEntity("/api/v1/phases", phaseRequest(name, forProjectId), TaskPhaseResponse.class).getBody();
    }

    private TaskResponse createTask(String title, TaskStatus status, UUID userId, UUID phaseId) {
        return restTemplate.postForEntity("/api/v1/tasks", taskRequest(status, userId, phaseId), TaskResponse.class).getBody();
    }

    private TaskPhaseRequest phaseRequest(TaskPhaseName name) {
        return phaseRequest(name, projectId);
    }

    private TaskPhaseRequest phaseRequest(TaskPhaseName name, UUID forProjectId) {
        TaskPhaseRequest req = new TaskPhaseRequest();
        req.setName(name);
        req.setProjectId(forProjectId);
        return req;
    }

    /**
     * Polls GET /api/v1/phases?projectId until a phase with the given name appears (async creation).
     * Returns the phase ID once found; throws if not found within 5 seconds.
     */
    private UUID waitForPhase(UUID forProjectId, TaskPhaseName name) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            TaskPhaseResponse[] phases = restTemplate
                    .getForEntity("/api/v1/phases?projectId=" + forProjectId, TaskPhaseResponse[].class)
                    .getBody();
            if (phases != null) {
                java.util.Optional<TaskPhaseResponse> found = Arrays.stream(phases)
                        .filter(p -> name.equals(p.getName()))
                        .findFirst();
                if (found.isPresent()) return found.get().getId();
            }
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        throw new IllegalStateException("Phase " + name + " not found for project " + forProjectId + " within 5 s");
    }

    private TaskRequest taskRequest(TaskStatus status, UUID userId, UUID phaseId) {
        TaskRequest req = new TaskRequest();
        req.setTitle("Task");
        req.setDescription("desc");
        req.setStatus(status);
        req.setAssignedUserId(userId);
        req.setProjectId(projectId);
        req.setPhaseId(phaseId);
        req.setPlannedStart(java.time.Instant.parse("2026-04-01T08:00:00Z"));
        req.setPlannedEnd(java.time.Instant.parse("2026-04-30T17:00:00Z"));
        return req;
    }
}
