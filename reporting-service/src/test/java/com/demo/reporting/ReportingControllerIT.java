package com.demo.reporting;

import com.demo.common.config.KafkaTopics;
import com.demo.common.dto.TaskStatus;
import com.demo.common.event.TaskEvent;
import com.demo.reporting.dedup.ProcessedEventRepository;
import com.demo.reporting.dto.MyTaskResponse;
import com.demo.reporting.dto.ProjectTaskCountResponse;
import com.demo.reporting.model.ReportTask;
import com.demo.reporting.repository.ReportTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the reporting-service "My Tasks" endpoint. Exercises both the
 * REST layer (by seeding the projection repository directly) and the Kafka consumer
 * (by producing a raw JSON {@link TaskEvent} and asserting the projection row appears).
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
@Import(TestSecurityConfig.class)
class ReportingControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ReportTaskRepository repository;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    void myTasks_returnsTasksAssignedToCurrentUser() {
        repository.save(buildTask(UUID.randomUUID(), TestSecurityConfig.TEST_USER_ID, "T-1", Instant.now()));
        repository.save(buildTask(UUID.randomUUID(), UUID.randomUUID(), "T-OTHER", Instant.now()));

        List<MyTaskResponse> body = getMyTasks(null);

        assertThat(body).hasSize(1);
        assertThat(body.get(0).getTaskCode()).isEqualTo("T-1");
    }

    @Test
    void myTasks_daysFilterExcludesOldRows() {
        repository.save(buildTask(UUID.randomUUID(), TestSecurityConfig.TEST_USER_ID, "RECENT",
                Instant.now().minus(2, ChronoUnit.DAYS)));
        repository.save(buildTask(UUID.randomUUID(), TestSecurityConfig.TEST_USER_ID, "OLD",
                Instant.now().minus(20, ChronoUnit.DAYS)));

        List<MyTaskResponse> last5 = getMyTasks(5);
        assertThat(last5).extracting(MyTaskResponse::getTaskCode).containsExactly("RECENT");

        List<MyTaskResponse> last30 = getMyTasks(30);
        assertThat(last30).extracting(MyTaskResponse::getTaskCode).containsExactlyInAnyOrder("RECENT", "OLD");
    }

    @Test
    void consumer_projectsTaskEventFromKafka() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskEvent event = TaskEvent.created(taskId, "KAFKA-1", "Kafka task", "via kafka",
                TaskStatus.IN_PROGRESS,
                UUID.randomUUID(), "Proj",
                null, null,
                TestSecurityConfig.TEST_USER_ID, "Tester",
                null, null);

        kafkaTemplate.send(KafkaTopics.TASK_EVENTS, taskId.toString(), objectMapper.writeValueAsString(event));

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(repository.findById(taskId)).isPresent());

        List<MyTaskResponse> body = getMyTasks(null);
        assertThat(body).anyMatch(t -> "KAFKA-1".equals(t.getTaskCode()));
    }

    @Test
    void duplicateTaskEvent_projectsOnce() throws Exception {
        UUID taskId = UUID.randomUUID();
        // Same object → same eventId → second delivery must be discarded by dedup
        TaskEvent event = TaskEvent.created(taskId, "DUP-1", "Dup task", "desc",
                TaskStatus.IN_PROGRESS,
                UUID.randomUUID(), "Proj",
                null, null,
                TestSecurityConfig.TEST_USER_ID, "Tester",
                null, null);
        String payload = objectMapper.writeValueAsString(event);

        kafkaTemplate.send(KafkaTopics.TASK_EVENTS, taskId.toString(), payload);
        kafkaTemplate.send(KafkaTopics.TASK_EVENTS, taskId.toString(), payload);

        // pollDelay: the second delivery is silently discarded by dedup, so the
        // first Awaitility check would pass after only message #1 is processed —
        // leaving message #2 in the consumer pipeline and leaking into the next test.
        // Delay the first poll long enough for both events to be committed.
        Awaitility.await()
                .pollDelay(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    assertThat(repository.findById(taskId)).isPresent();
                    assertThat(processedEventRepository.count()).isEqualTo(1);
                });
    }

    @Test
    void openTasksByProject_returnsCounts() {
        UUID projectAId = UUID.randomUUID();
        UUID projectBId = UUID.randomUUID();
        UUID otherUser  = UUID.randomUUID();

        // Project A: 2 mine + 1 other = 3 total
        repository.save(buildTaskInProject(UUID.randomUUID(), TestSecurityConfig.TEST_USER_ID, "A-1",
                projectAId, "Project A"));
        repository.save(buildTaskInProject(UUID.randomUUID(), TestSecurityConfig.TEST_USER_ID, "A-2",
                projectAId, "Project A"));
        repository.save(buildTaskInProject(UUID.randomUUID(), otherUser, "A-3",
                projectAId, "Project A"));

        // Project B: 1 mine = 1 total
        repository.save(buildTaskInProject(UUID.randomUUID(), TestSecurityConfig.TEST_USER_ID, "B-1",
                projectBId, "Project B"));

        ResponseEntity<List<ProjectTaskCountResponse>> response = restTemplate.exchange(
                "/api/v1/reports/tasks/open-by-project", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ProjectTaskCountResponse> body = response.getBody();
        assertThat(body).isNotNull();

        ProjectTaskCountResponse projA = body.stream()
                .filter(r -> projectAId.equals(r.getProjectId())).findFirst().orElseThrow();
        assertThat(projA.getTotalOpenCount()).isEqualTo(3);
        assertThat(projA.getMyOpenCount()).isEqualTo(2);

        ProjectTaskCountResponse projB = body.stream()
                .filter(r -> projectBId.equals(r.getProjectId())).findFirst().orElseThrow();
        assertThat(projB.getTotalOpenCount()).isEqualTo(1);
        assertThat(projB.getMyOpenCount()).isEqualTo(1);
    }

    private List<MyTaskResponse> getMyTasks(Integer days) {
        String url = days == null ? "/api/v1/reports/my-tasks" : "/api/v1/reports/my-tasks?days=" + days;
        ResponseEntity<List<MyTaskResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private ReportTask buildTask(UUID id, UUID assignedUserId, String taskCode, Instant updatedAt) {
        ReportTask t = new ReportTask();
        t.setId(id);
        t.setTaskCode(taskCode);
        t.setTitle("Title " + taskCode);
        t.setDescription("desc");
        t.setStatus(TaskStatus.TODO);
        t.setAssignedUserId(assignedUserId);
        t.setUpdatedAt(updatedAt);
        return t;
    }

    /** Builds a task with project info set — used for open-tasks-by-project tests. */
    private ReportTask buildTaskInProject(UUID id, UUID assignedUserId, String taskCode,
                                          UUID projectId, String projectName) {
        ReportTask t = buildTask(id, assignedUserId, taskCode, Instant.now());
        t.setProjectId(projectId);
        t.setProjectName(projectName);
        return t;
    }
}
