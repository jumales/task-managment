package com.demo.task;

import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.UserDto;
import com.demo.task.client.UserClient;
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
class TaskProjectControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskProjectRepository projectRepository;

    @Autowired
    TaskRepository taskRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        when(userClient.getUserById(ALICE_ID))
                .thenReturn(new UserDto(ALICE_ID, "Alice", "alice@demo.com", null, true, null));
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
        assertThat(projectRepository.count()).isEqualTo(1);
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

        ResponseEntity<TaskResponse[]> response =
                restTemplate.getForEntity("/api/v1/tasks?projectId=" + projectA.getId(), TaskResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allSatisfy(t ->
                assertThat(t.getProject().getId()).isEqualTo(projectA.getId()));
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

        ResponseEntity<TaskResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks", taskRequest("Task A", TaskStatus.TODO, ALICE_ID, project.getId()), TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getProject()).isNotNull();
        assertThat(response.getBody().getProject().getId()).isEqualTo(project.getId());
        assertThat(response.getBody().getProject().getName()).isEqualTo("Embedded Project");
    }

    @Test
    void createTask_withNonExistentProject_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks", taskRequest("Bad Task", TaskStatus.TODO, ALICE_ID, UUID.randomUUID()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(taskRepository.count()).isEqualTo(0);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private TaskProjectResponse createProject(String name, String description) {
        return restTemplate.postForEntity("/api/v1/projects", projectRequest(name, description), TaskProjectResponse.class).getBody();
    }

    private TaskResponse createTask(String title, TaskStatus status, UUID userId, UUID projectId) {
        return restTemplate.postForEntity("/api/v1/tasks", taskRequest(title, status, userId, projectId), TaskResponse.class).getBody();
    }

    private TaskProjectRequest projectRequest(String name, String description) {
        TaskProjectRequest req = new TaskProjectRequest();
        req.setName(name);
        req.setDescription(description);
        return req;
    }

    private TaskRequest taskRequest(String title, TaskStatus status, UUID userId, UUID projectId) {
        TaskRequest req = new TaskRequest();
        req.setTitle(title);
        req.setDescription("desc");
        req.setStatus(status);
        req.setAssignedUserId(userId);
        req.setProjectId(projectId);
        return req;
    }
}
