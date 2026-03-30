package com.demo.task;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.UserDto;
import com.demo.common.web.ControllerLoggingAspect;
import com.demo.task.client.UserClient;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskTimelineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class ControllerLoggingAspectIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TaskRepository repository;

    @Autowired
    TaskProjectRepository projectRepository;

    @Autowired
    TaskTimelineRepository timelineRepository;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger aspectLogger;

    private static final UUID USER_ID = UUID.randomUUID();
    private UUID projectId;

    @BeforeEach
    void setUp() {
        timelineRepository.deleteAll();
        repository.deleteAll();
        projectRepository.deleteAll();

        when(userClient.getUserById(USER_ID)).thenReturn(new UserDto(USER_ID, "Alice", "alice@demo.com", null, true, null, null, "en"));

        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Test Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();

        // Attach appender after setup calls so captured logs are test-specific only
        aspectLogger = (Logger) LoggerFactory.getLogger(ControllerLoggingAspect.class);
        aspectLogger.setLevel(Level.DEBUG);
        listAppender = new ListAppender<>();
        listAppender.start();
        aspectLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(listAppender);
    }

    @Test
    void shouldLogControllerNameAndMethodName() {
        restTemplate.getForEntity("/api/v1/tasks", Object.class);

        assertThat(listAppender.list)
                .anyMatch(e -> e.getLevel() == Level.DEBUG
                        && e.getFormattedMessage().contains("TaskController")
                        && e.getFormattedMessage().contains("getAll"));
    }

    @Test
    void shouldLogQueryParameterValue() {
        restTemplate.getForEntity("/api/v1/tasks?status=TODO", Object.class);

        assertThat(listAppender.list)
                .anyMatch(e -> e.getLevel() == Level.DEBUG
                        && e.getFormattedMessage().contains("getAll")
                        && e.getFormattedMessage().contains("status=TODO"));
    }

    @Test
    void shouldLogPathVariableValue() {
        UUID taskId = UUID.randomUUID();

        restTemplate.getForEntity("/api/v1/tasks/" + taskId, Object.class);

        assertThat(listAppender.list)
                .anyMatch(e -> e.getLevel() == Level.DEBUG
                        && e.getFormattedMessage().contains("getById")
                        && e.getFormattedMessage().contains(taskId.toString()));
    }

    @Test
    void shouldLogRequestBodyParamName() {
        TaskRequest req = new TaskRequest();
        req.setTitle("Logging test task");
        req.setDescription("desc");
        req.setStatus(TaskStatus.TODO);
        req.setAssignedUserId(USER_ID);
        req.setProjectId(projectId);
        req.setPlannedStart(java.time.Instant.parse("2026-04-01T08:00:00Z"));
        req.setPlannedEnd(java.time.Instant.parse("2026-04-30T17:00:00Z"));

        restTemplate.postForEntity("/api/v1/tasks", req, Object.class);

        assertThat(listAppender.list)
                .anyMatch(e -> e.getLevel() == Level.DEBUG
                        && e.getFormattedMessage().contains("create")
                        && e.getFormattedMessage().contains("request"));
    }

    @Test
    void shouldLogNullForOmittedOptionalParameters() {
        // Calling getAll with no query params — userId, projectId, status are all null
        restTemplate.getForEntity("/api/v1/tasks", Object.class);

        assertThat(listAppender.list)
                .anyMatch(e -> e.getLevel() == Level.DEBUG
                        && e.getFormattedMessage().contains("getAll")
                        && e.getFormattedMessage().contains("userId=null")
                        && e.getFormattedMessage().contains("status=null"));
    }

    @Test
    void shouldNotProduceLogsWhenLevelIsAboveDebug() {
        aspectLogger.setLevel(Level.INFO);

        restTemplate.getForEntity("/api/v1/tasks", Object.class);

        assertThat(listAppender.list).isEmpty();
    }
}
