package com.demo.task;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskPhaseName;
import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskSummaryResponse;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskProjectControllerIT {

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
    /** Tracks the default BACKLOG phase per project so tasks can always be created with a valid phase. */
    private final java.util.Map<UUID, UUID> defaultPhaseByProject = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();
        defaultPhaseByProject.clear();
        when(userClient.getUserById(ALICE_ID))
                .thenReturn(new UserDto(ALICE_ID, "Alice", "alice@demo.com", null, true, null, null, "en"));
    }

    // ── GET /api/v1/projects ─────────────────────────────────────────

    @Test
    void getAllProjects_whenEmpty_returnsEmptyList() {
        ResponseEntity<TaskProjectResponse[]> response =
                restTemplate.getForEntity("/api/v1/projects", TaskProjectResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getAllProjects_returnsAllCreatedProjects() {
        createProject("Alpha", "First project");
        createProject("Beta", "Second project");

        ResponseEntity<TaskProjectResponse[]> response =
                restTemplate.getForEntity("/api/v1/projects", TaskProjectResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).extracting("name")
                .containsExactlyInAnyOrder("Alpha", "Beta");
    }

    // ── GET /api/v1/projects/{id} ────────────────────────────────────

    @Test
    void getProjectById_returnsProject() {
        TaskProjectResponse created = createProject("My Project", "A description");

        ResponseEntity<TaskProjectResponse> response =
                restTemplate.getForEntity("/api/v1/projects/" + created.getId(), TaskProjectResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("My Project");
        assertThat(response.getBody().getDescription()).isEqualTo("A description");
    }

    @Test
    void getProjectById_whenNotFound_returns404() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/projects/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/v1/projects ────────────────────────────────────────

    @Test
    void createProject_persistsAndReturnsProject() {
        ResponseEntity<TaskProjectResponse> response =
                restTemplate.postForEntity("/api/v1/projects", projectRequest("Sprint 1", "Q1 sprint"), TaskProjectResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Sprint 1");
        assertThat(response.getBody().getDescription()).isEqualTo("Q1 sprint");
        assertThat(response.getBody().getTaskCodePrefix()).isEqualTo("TASK_");
        assertThat(projectRepository.count()).isEqualTo(1);
    }

    @Test
    void createProject_withCustomPrefix_usesProvidedPrefix() {
        TaskProjectRequest req = projectRequest("My Project", null);
        req.setTaskCodePrefix("MP_");

        ResponseEntity<TaskProjectResponse> response =
                restTemplate.postForEntity("/api/v1/projects", req, TaskProjectResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getTaskCodePrefix()).isEqualTo("MP_");
    }

    @Test
    void createTask_taskCodeIsGeneratedWithProjectPrefix() {
        TaskProjectRequest req = projectRequest("Coded Project", null);
        req.setTaskCodePrefix("CP_");
        TaskProjectResponse project = restTemplate.postForEntity("/api/v1/projects", req, TaskProjectResponse.class).getBody();

        TaskResponse first  = createTask("Task A", TaskStatus.TODO, ALICE_ID, project.getId());
        TaskResponse second = createTask("Task B", TaskStatus.TODO, ALICE_ID, project.getId());

        assertThat(first.getTaskCode()).isEqualTo("CP_1");
        assertThat(second.getTaskCode()).isEqualTo("CP_2");
    }

    // ── PUT /api/v1/projects/{id} ────────────────────────────────────

    @Test
    void updateProject_updatesNameAndDescription() {
        TaskProjectResponse created = createProject("Old Name", "Old desc");

        ResponseEntity<TaskProjectResponse> response = restTemplate.exchange(
                "/api/v1/projects/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(projectRequest("New Name", "New desc")),
                TaskProjectResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("New Name");
        assertThat(response.getBody().getDescription()).isEqualTo("New desc");
    }

    @Test
    void updateProject_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects/" + UUID.randomUUID(),
                HttpMethod.PUT,
                new HttpEntity<>(projectRequest("X", "Y")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/v1/projects/{id} ─────────────────────────────────

    @Test
    void deleteProject_softDeletesProject() {
        TaskProjectResponse created = createProject("To Delete", "desc");

        restTemplate.delete("/api/v1/projects/" + created.getId());

        // project no longer returned by GET
        assertThat(restTemplate.getForEntity("/api/v1/projects", TaskProjectResponse[].class).getBody()).isEmpty();
        // GET by id also returns 404
        assertThat(restTemplate.getForEntity("/api/v1/projects/" + created.getId(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        // row still exists in DB but with deletedAt set
        assertThat(projectRepository.count()).isEqualTo(0); // @SQLRestriction filters it out
    }

    @Test
    void deleteProject_withActiveTasks_returns409() {
        TaskProjectResponse project = createProject("Blocked Project", null);
        createTask("Blocking Task", TaskStatus.TODO, ALICE_ID, project.getId());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects/" + project.getId(), HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(projectRepository.count()).isEqualTo(1);
    }

    @Test
    void deleteProject_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects/" + UUID.randomUUID(),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Tasks filtered by project ─────────────────────────────────

    @Test
    void getTasksByProjectId_returnsOnlyTasksInThatProject() {
        TaskProjectResponse projectA = createProject("Project A", null);
        TaskProjectResponse projectB = createProject("Project B", null);

        createTask("Task 1", TaskStatus.TODO, ALICE_ID, projectA.getId());
        createTask("Task 2", TaskStatus.TODO, ALICE_ID, projectA.getId());
        createTask("Task 3", TaskStatus.TODO, ALICE_ID, projectB.getId());

        ResponseEntity<PageResponse<TaskSummaryResponse>> response = restTemplate.exchange(
                "/api/v1/tasks?projectId=" + projectA.getId(),
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(2);
        assertThat(response.getBody().getContent()).allSatisfy(t ->
                assertThat(t.getProjectId()).isEqualTo(projectA.getId()));
    }

    @Test
    void getTasksByProjectId_forUnknownProject_returns404() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/tasks?projectId=" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createTask_embeddedProjectInResponse() {
        TaskProjectResponse project = createProject("Embedded Project", "desc");
        UUID phaseId = getOrCreateDefaultPhase(project.getId());

        ResponseEntity<TaskResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks", taskRequest("Task A", TaskStatus.TODO, ALICE_ID, project.getId(), phaseId), TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getProject()).isNotNull();
        assertThat(response.getBody().getProject().getId()).isEqualTo(project.getId());
        assertThat(response.getBody().getProject().getName()).isEqualTo("Embedded Project");
    }

    @Test
    void createTask_withNonExistentProject_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks", taskRequest("Bad Task", TaskStatus.TODO, ALICE_ID, UUID.randomUUID(), null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(taskRepository.count()).isEqualTo(0);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private TaskProjectResponse createProject(String name, String description) {
        return restTemplate.postForEntity("/api/v1/projects", projectRequest(name, description), TaskProjectResponse.class).getBody();
    }

    private TaskResponse createTask(String title, TaskStatus status, UUID userId, UUID projectId) {
        UUID phaseId = getOrCreateDefaultPhase(projectId);
        return restTemplate.postForEntity("/api/v1/tasks", taskRequest(title, status, userId, projectId, phaseId), TaskResponse.class).getBody();
    }

    /** Returns the BACKLOG phase for the given project, creating one if it does not exist yet. */
    private UUID getOrCreateDefaultPhase(UUID projectId) {
        return defaultPhaseByProject.computeIfAbsent(projectId, pid -> {
            TaskPhaseRequest req = new TaskPhaseRequest();
            req.setName(TaskPhaseName.BACKLOG);
            req.setProjectId(pid);
            UUID phaseId = restTemplate.postForEntity("/api/v1/phases", req, TaskPhaseResponse.class).getBody().getId();
            // Set this phase as the project's default so task creation without explicit phaseId also works.
            TaskProjectRequest projectReq = new TaskProjectRequest();
            projectReq.setName("project");
            projectReq.setDefaultPhaseId(phaseId);
            restTemplate.exchange("/api/v1/projects/" + pid, HttpMethod.PUT,
                    new HttpEntity<>(projectReq), TaskProjectResponse.class);
            return phaseId;
        });
    }

    private TaskProjectRequest projectRequest(String name, String description) {
        TaskProjectRequest req = new TaskProjectRequest();
        req.setName(name);
        req.setDescription(description);
        return req;
    }

    private TaskRequest taskRequest(String title, TaskStatus status, UUID userId, UUID projectId, UUID phaseId) {
        TaskRequest req = new TaskRequest();
        req.setTitle(title);
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
