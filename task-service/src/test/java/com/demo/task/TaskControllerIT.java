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
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskRepository repository;

    @Autowired
    TaskProjectRepository projectRepository;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID   = UUID.randomUUID();

    private UserDto alice;
    private UserDto bob;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        projectRepository.deleteAll();

        alice = new UserDto(ALICE_ID, "Alice Johnson", "alice@demo.com", null);
        bob   = new UserDto(BOB_ID,   "Bob Smith",     "bob@demo.com",   null);

        when(userClient.getUserById(ALICE_ID)).thenReturn(alice);
        when(userClient.getUserById(BOB_ID)).thenReturn(bob);

        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Default Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();
    }

    // ── GET /api/v1/tasks ────────────────────────────────────────────

    @Test
    void getAllTasks_whenEmpty_returnsEmptyList() {
        ResponseEntity<TaskResponse[]> response = restTemplate.getForEntity("/api/v1/tasks", TaskResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getAllTasks_returnsTasksWithUserDetails() {
        restTemplate.postForEntity("/api/v1/tasks", request("Setup CI", "Configure pipeline", TaskStatus.DONE, ALICE_ID), TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Write tests", "Unit tests", TaskStatus.TODO, BOB_ID), TaskResponse.class);

        ResponseEntity<TaskResponse[]> response = restTemplate.getForEntity("/api/v1/tasks", TaskResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()[0].getAssignedUser()).isNotNull();
        assertThat(response.getBody()[0].getAssignedUser().getName()).isEqualTo("Alice Johnson");
    }

    // ── GET /api/v1/tasks/{id} ───────────────────────────────────────

    @Test
    void getTaskById_returnsTaskWithUserDetails() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("Implement login", "JWT auth", TaskStatus.IN_PROGRESS, BOB_ID), TaskResponse.class).getBody();

        ResponseEntity<TaskResponse> response = restTemplate.getForEntity("/api/v1/tasks/" + created.getId(), TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTitle()).isEqualTo("Implement login");
        assertThat(response.getBody().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.getBody().getAssignedUser().getName()).isEqualTo("Bob Smith");
    }

    @Test
    void getTaskById_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/tasks/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/v1/tasks?userId={id} ────────────────────────────────

    @Test
    void getTasksByUserId_returnsOnlyTasksAssignedToThatUser() {
        restTemplate.postForEntity("/api/v1/tasks", request("Task A", "For Alice",      TaskStatus.TODO, ALICE_ID), TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Task B", "For Bob",        TaskStatus.TODO, BOB_ID),   TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Task C", "Also for Alice", TaskStatus.DONE, ALICE_ID), TaskResponse.class);

        ResponseEntity<TaskResponse[]> response = restTemplate.getForEntity("/api/v1/tasks?userId=" + ALICE_ID, TaskResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allSatisfy(t -> assertThat(t.getAssignedUser().getId()).isEqualTo(ALICE_ID));
    }

    // ── GET /api/v1/tasks?status={status} ───────────────────────────

    @Test
    void getTasksByStatus_returnsOnlyMatchingTasks() {
        restTemplate.postForEntity("/api/v1/tasks", request("Task A", "desc", TaskStatus.TODO,        ALICE_ID), TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Task B", "desc", TaskStatus.IN_PROGRESS, BOB_ID),   TaskResponse.class);
        restTemplate.postForEntity("/api/v1/tasks", request("Task C", "desc", TaskStatus.TODO,        ALICE_ID), TaskResponse.class);

        ResponseEntity<TaskResponse[]> response = restTemplate.getForEntity("/api/v1/tasks?status=TODO", TaskResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TaskStatus.TODO));
    }

    @Test
    void getTasksByStatus_caseInsensitive_returnsMatchingTasks() {
        restTemplate.postForEntity("/api/v1/tasks", request("Task A", "desc", TaskStatus.DONE, ALICE_ID), TaskResponse.class);

        ResponseEntity<TaskResponse[]> response = restTemplate.getForEntity("/api/v1/tasks?status=done", TaskResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    // ── POST /api/v1/tasks ───────────────────────────────────────────

    @Test
    void createTask_persistsAndReturnsTask() {
        ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/api/v1/tasks", request("New feature", "Implement OAuth2", TaskStatus.TODO, ALICE_ID), TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("New feature");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void createTask_withNonExistentUser_returns500() {
        when(userClient.getUserById(any(UUID.class))).thenThrow(new RuntimeException("User not found"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/tasks", request("Ghost task", "desc", TaskStatus.TODO, UUID.randomUUID()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(repository.count()).isEqualTo(0);
    }

    // ── PUT /api/v1/tasks/{id} ───────────────────────────────────────

    @Test
    void updateTask_updatesAllFields() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("Old title", "Old desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();

        ResponseEntity<TaskResponse> response = restTemplate.exchange(
                "/api/v1/tasks/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request("New title", "New desc", TaskStatus.IN_PROGRESS, BOB_ID)),
                TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTitle()).isEqualTo("New title");
        assertThat(response.getBody().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.getBody().getAssignedUser().getId()).isEqualTo(BOB_ID);
    }

    // ── DELETE /api/v1/tasks/{id} ────────────────────────────────────

    @Test
    void deleteTask_removesTaskFromDatabase() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("To delete", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();

        restTemplate.delete("/api/v1/tasks/" + created.getId());

        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void deleteTask_thenGetById_returns404() {
        TaskResponse created = restTemplate.postForEntity("/api/v1/tasks", request("To delete", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class).getBody();

        restTemplate.delete("/api/v1/tasks/" + created.getId());

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/tasks/" + created.getId(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteProject_withActiveTasks_returns409() {
        restTemplate.postForEntity("/api/v1/tasks", request("Blocking task", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repository.count()).isEqualTo(1);
    }

    // ── Resilience ────────────────────────────────────────────────

    @Test
    void getAllTasks_whenUserServiceDown_returnsTasksWithNullUser() {
        restTemplate.postForEntity("/api/v1/tasks", request("Orphan task", "desc", TaskStatus.TODO, ALICE_ID), TaskResponse.class);
        when(userClient.getUserById(any(UUID.class))).thenThrow(new RuntimeException("user-service unavailable"));

        ResponseEntity<TaskResponse[]> response = restTemplate.getForEntity("/api/v1/tasks", TaskResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getAssignedUser()).isNull();
    }

    // ── Helper ────────────────────────────────────────────────────

    private TaskRequest request(String title, String description, TaskStatus status, UUID userId) {
        TaskRequest req = new TaskRequest();
        req.setTitle(title);
        req.setDescription(description);
        req.setStatus(status);
        req.setAssignedUserId(userId);
        req.setProjectId(projectId);
        return req;
    }
}
