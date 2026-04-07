package com.demo.task;

import com.demo.common.dto.TaskAttachmentRequest;
import com.demo.common.dto.TaskAttachmentResponse;
import com.demo.common.dto.TaskPhaseName;
import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskPhaseUpdateRequest;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskType;
import com.demo.common.dto.UserDto;
import com.demo.task.client.FileClient;
import com.demo.task.client.UserClient;
import com.demo.task.repository.TaskAttachmentRepository;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link com.demo.task.controller.TaskAttachmentController}.
 */
@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskAttachmentControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    UserClient userClient;

    /** Mocked so file-service calls are no-ops; verify interaction on delete. */
    @MockitoBean
    FileClient fileClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskAttachmentRepository attachmentRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskProjectRepository projectRepository;

    @Autowired
    TaskPhaseRepository phaseRepository;

    @Autowired
    TaskPlannedWorkRepository plannedWorkRepository;

    @Autowired
    TaskTimelineRepository timelineRepository;

    private static final UUID TEST_USER_ID = TestSecurityConfig.TEST_USER_ID;
    private static final String TEST_USER_NAME = "Test Admin";

    private String taskId;
    private UUID projectId;
    private UUID phaseId;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
        plannedWorkRepository.deleteAll();
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();

        UserDto testUser = new UserDto(TEST_USER_ID, TEST_USER_NAME, "admin@test.com", null, true, null, "en");
        when(userClient.getUserById(TEST_USER_ID)).thenReturn(testUser);
        when(userClient.getUsersByIds(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return List.of(testUser).stream().filter(u -> ids.contains(u.getId())).toList();
        });

        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Test Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();

        TaskPhaseRequest phaseReq = new TaskPhaseRequest();
        phaseReq.setName(TaskPhaseName.BACKLOG);
        phaseReq.setProjectId(projectId);
        phaseId = restTemplate.postForEntity("/api/v1/phases", phaseReq, TaskPhaseResponse.class).getBody().getId();

        TaskProjectRequest defaultPhaseReq = new TaskProjectRequest();
        defaultPhaseReq.setName("Test Project");
        defaultPhaseReq.setDefaultPhaseId(phaseId);
        restTemplate.exchange("/api/v1/projects/" + projectId, HttpMethod.PUT,
                new HttpEntity<>(defaultPhaseReq), TaskProjectResponse.class);

        TaskRequest taskReq = new TaskRequest();
        taskReq.setTitle("Sample Task");
        taskReq.setStatus(TaskStatus.TODO);
        taskReq.setAssignedUserId(TEST_USER_ID);
        taskReq.setProjectId(projectId);
        taskReq.setPhaseId(phaseId);
        taskReq.setType(TaskType.FEATURE);
        taskReq.setPlannedStart(Instant.parse("2026-04-01T08:00:00Z"));
        taskReq.setPlannedEnd(Instant.parse("2026-04-30T17:00:00Z"));
        taskId = restTemplate.postForEntity("/api/v1/tasks", taskReq, TaskResponse.class)
                .getBody().getId().toString();
    }

    // ── GET /api/v1/tasks/{taskId}/attachments ───────────────────────────────

    @Test
    void getAttachments_whenNoneExist_returnsEmptyList() {
        ResponseEntity<TaskAttachmentResponse[]> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/attachments",
                TaskAttachmentResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getAttachments_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/attachments",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /api/v1/tasks/{taskId}/attachments ──────────────────────────────

    @Test
    void addAttachment_persistsAndReturns201() {
        UUID fileId = UUID.randomUUID();
        TaskAttachmentRequest request = attachmentRequest(fileId, "design.png", "image/png");

        ResponseEntity<TaskAttachmentResponse> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/attachments",
                request,
                TaskAttachmentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TaskAttachmentResponse body = response.getBody();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getFileId()).isEqualTo(fileId);
        assertThat(body.getFileName()).isEqualTo("design.png");
        assertThat(body.getContentType()).isEqualTo("image/png");
        assertThat(body.getUploadedByUserId()).isEqualTo(TEST_USER_ID);
        assertThat(body.getUploadedByUserName()).isEqualTo(TEST_USER_NAME);
        assertThat(body.getUploadedAt()).isNotNull();
    }

    @Test
    void addAttachment_whenTaskNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/tasks/" + UUID.randomUUID() + "/attachments",
                attachmentRequest(UUID.randomUUID(), "file.txt", "text/plain"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAttachments_afterAdding_returnsOneItem() {
        restTemplate.postForEntity("/api/v1/tasks/" + taskId + "/attachments",
                attachmentRequest(UUID.randomUUID(), "spec.pdf", "application/pdf"),
                TaskAttachmentResponse.class);

        ResponseEntity<TaskAttachmentResponse[]> listResponse = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/attachments",
                TaskAttachmentResponse[].class);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody()[0].getFileName()).isEqualTo("spec.pdf");
    }

    // ── DELETE /api/v1/tasks/{taskId}/attachments/{attachmentId} ─────────────

    @Test
    void deleteAttachment_returns204_andCallsFileService() {
        UUID fileId = UUID.randomUUID();
        UUID attachmentId = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/attachments",
                attachmentRequest(fileId, "design.png", "image/png"),
                TaskAttachmentResponse.class).getBody().getId();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/attachments/" + attachmentId,
                HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // Non-JWT test auth returns null bearer token; verify file-service was called
        verify(fileClient).deleteFile(fileId, null);
    }

    @Test
    void deleteAttachment_removesItFromList() {
        UUID attachmentId = restTemplate.postForEntity(
                "/api/v1/tasks/" + taskId + "/attachments",
                attachmentRequest(UUID.randomUUID(), "notes.txt", "text/plain"),
                TaskAttachmentResponse.class).getBody().getId();

        restTemplate.delete("/api/v1/tasks/" + taskId + "/attachments/" + attachmentId);

        ResponseEntity<TaskAttachmentResponse[]> listResponse = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId + "/attachments",
                TaskAttachmentResponse[].class);
        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void deleteAttachment_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/attachments/" + UUID.randomUUID(),
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private TaskAttachmentRequest attachmentRequest(UUID fileId, String fileName, String contentType) {
        TaskAttachmentRequest req = new TaskAttachmentRequest();
        req.setFileId(fileId);
        req.setFileName(fileName);
        req.setContentType(contentType);
        return req;
    }
}
