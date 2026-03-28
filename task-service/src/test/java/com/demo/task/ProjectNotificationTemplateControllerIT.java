package com.demo.task;

import com.demo.common.dto.ProjectNotificationTemplateRequest;
import com.demo.common.dto.ProjectNotificationTemplateResponse;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TemplatePlaceholder;
import com.demo.common.event.TaskChangeType;
import com.demo.task.client.UserClient;
import com.demo.task.repository.ProjectNotificationTemplateRepository;
import com.demo.task.repository.TaskProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link com.demo.task.controller.ProjectNotificationTemplateController}.
 */
@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class ProjectNotificationTemplateControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskProjectRepository projectRepository;

    @Autowired
    ProjectNotificationTemplateRepository templateRepository;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        templateRepository.deleteAll();
        projectRepository.deleteAll();

        TaskProjectRequest req = new TaskProjectRequest();
        req.setName("Demo Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", req, TaskProjectResponse.class)
                .getBody().getId();
    }

    // ── GET /api/v1/projects/{projectId}/notification-templates/placeholders ─

    @Test
    void getPlaceholders_returnsAllSupportedTokens() {
        ResponseEntity<TemplatePlaceholder[]> response = restTemplate.getForEntity(
                "/api/v1/projects/" + projectId + "/notification-templates/placeholders",
                TemplatePlaceholder[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(TemplatePlaceholder.values().length);
        assertThat(response.getBody()).contains(TemplatePlaceholder.TASK_URL, TemplatePlaceholder.USER_NAME);
    }

    // ── GET /api/v1/projects/{projectId}/notification-templates ─────────────

    @Test
    void getAll_whenNoneConfigured_returnsEmptyList() {
        ResponseEntity<ProjectNotificationTemplateResponse[]> response = restTemplate.getForEntity(
                "/api/v1/projects/" + projectId + "/notification-templates",
                ProjectNotificationTemplateResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getAll_whenProjectNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/projects/" + UUID.randomUUID() + "/notification-templates",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/v1/projects/{projectId}/notification-templates/{eventType} ─

    @Test
    void getByEventType_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/projects/" + projectId + "/notification-templates/TASK_CREATED",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── PUT /api/v1/projects/{projectId}/notification-templates/{eventType} ─

    @Test
    void upsert_createsNewTemplate() {
        ProjectNotificationTemplateRequest request = templateRequest(
                "New task: {taskTitle}",
                "Hello, a new task '{taskTitle}' has been created in project {projectId}.");

        ResponseEntity<ProjectNotificationTemplateResponse> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/notification-templates/TASK_CREATED",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                ProjectNotificationTemplateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getProjectId()).isEqualTo(projectId);
        assertThat(response.getBody().getEventType()).isEqualTo(TaskChangeType.TASK_CREATED);
        assertThat(response.getBody().getSubjectTemplate()).isEqualTo("New task: {taskTitle}");
        assertThat(response.getBody().getBodyTemplate()).contains("{taskTitle}");
    }

    @Test
    void upsert_replacesExistingTemplate() {
        // First upsert
        restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/notification-templates/STATUS_CHANGED",
                HttpMethod.PUT,
                new HttpEntity<>(templateRequest("Old subject", "Old body")),
                ProjectNotificationTemplateResponse.class);

        // Second upsert — should replace
        ResponseEntity<ProjectNotificationTemplateResponse> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/notification-templates/STATUS_CHANGED",
                HttpMethod.PUT,
                new HttpEntity<>(templateRequest("New subject", "New body")),
                ProjectNotificationTemplateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSubjectTemplate()).isEqualTo("New subject");

        // Only one active template should remain
        ResponseEntity<ProjectNotificationTemplateResponse[]> listResponse = restTemplate.getForEntity(
                "/api/v1/projects/" + projectId + "/notification-templates",
                ProjectNotificationTemplateResponse[].class);
        assertThat(listResponse.getBody()).hasSize(1);
    }

    @Test
    void upsert_persistedTemplateIsReturnedByGet() {
        restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/notification-templates/COMMENT_ADDED",
                HttpMethod.PUT,
                new HttpEntity<>(templateRequest("Comment: {taskTitle}", "Body text")),
                ProjectNotificationTemplateResponse.class);

        ResponseEntity<ProjectNotificationTemplateResponse> response = restTemplate.getForEntity(
                "/api/v1/projects/" + projectId + "/notification-templates/COMMENT_ADDED",
                ProjectNotificationTemplateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSubjectTemplate()).isEqualTo("Comment: {taskTitle}");
    }

    @Test
    void upsert_whenProjectNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects/" + UUID.randomUUID() + "/notification-templates/TASK_CREATED",
                HttpMethod.PUT,
                new HttpEntity<>(templateRequest("s", "b")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void upsert_withUnknownPlaceholder_returns400() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/notification-templates/TASK_CREATED",
                HttpMethod.PUT,
                new HttpEntity<>(templateRequest("Hello {typo}", "Body {unknownVar}")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void upsert_withNewPlaceholders_taskUrlAndUserName_persists() {
        ResponseEntity<ProjectNotificationTemplateResponse> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/notification-templates/TASK_CREATED",
                HttpMethod.PUT,
                new HttpEntity<>(templateRequest(
                        "Hi {userName}, new task: {taskTitle}",
                        "View it at {taskUrl}")),
                ProjectNotificationTemplateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSubjectTemplate()).contains("{userName}");
        assertThat(response.getBody().getBodyTemplate()).contains("{taskUrl}");
    }

    // ── DELETE /api/v1/projects/{projectId}/notification-templates/{eventType}

    @Test
    void delete_removesTemplate() {
        restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/notification-templates/PHASE_CHANGED",
                HttpMethod.PUT,
                new HttpEntity<>(templateRequest("Phase: {toPhase}", "Moved to {toPhase}")),
                ProjectNotificationTemplateResponse.class);

        restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/notification-templates/PHASE_CHANGED",
                HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/projects/" + projectId + "/notification-templates/PHASE_CHANGED",
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects/" + projectId + "/notification-templates/TASK_CREATED",
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ProjectNotificationTemplateRequest templateRequest(String subject, String body) {
        ProjectNotificationTemplateRequest req = new ProjectNotificationTemplateRequest();
        req.setSubjectTemplate(subject);
        req.setBodyTemplate(body);
        return req;
    }
}
