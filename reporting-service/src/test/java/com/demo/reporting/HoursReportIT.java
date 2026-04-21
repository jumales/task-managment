package com.demo.reporting;

import com.demo.common.config.KafkaTopics;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.WorkType;
import com.demo.common.event.TaskChangedEvent;
import com.demo.reporting.dedup.ProcessedEventRepository;
import com.demo.reporting.dto.DetailedHoursResponse;
import com.demo.reporting.dto.ProjectHoursResponse;
import com.demo.reporting.dto.TaskHoursResponse;
import com.demo.reporting.model.ReportTask;
import com.demo.reporting.repository.ReportBookedWorkRepository;
import com.demo.reporting.repository.ReportPlannedWorkRepository;
import com.demo.reporting.repository.ReportTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the hours report. Publishes {@link TaskChangedEvent} messages to the
 * {@code task-changed} topic and asserts the aggregation endpoints return the expected totals.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
@Import(TestSecurityConfig.class)
class HoursReportIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ReportTaskRepository taskRepository;
    @Autowired private ReportPlannedWorkRepository plannedRepository;
    @Autowired private ReportBookedWorkRepository bookedRepository;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProcessedEventRepository processedEventRepository;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void cleanUp() {
        // Physical deletes — soft-delete (@SQLDelete) would leave rows in the DB and cause
        // duplicate-key failures when the same static TASK_ID is re-inserted in subsequent tests.
        jdbcTemplate.execute("DELETE FROM report_booked_works");
        jdbcTemplate.execute("DELETE FROM report_planned_works");
        jdbcTemplate.execute("DELETE FROM report_tasks");
        processedEventRepository.deleteAll();

        // Seed a parent task so by-task/by-project responses can enrich code/name.
        ReportTask task = new ReportTask();
        task.setId(TASK_ID);
        task.setTaskCode("T-HRS");
        task.setTitle("Hours task");
        task.setProjectId(PROJECT_ID);
        task.setProjectName("Project Hours");
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
    }

    @Test
    void hoursReport_aggregatesPlannedBookedAcrossLevels() throws Exception {
        // 10h planned + 4h booked (DEV) from events, plus a booked update that goes from 4 -> 7
        publish(TaskChangedEvent.plannedWorkCreated(TASK_ID, PROJECT_ID, "Hours task",
                UUID.randomUUID(), USER_ID, WorkType.DEVELOPMENT, BigInteger.valueOf(10)));

        UUID bookedId = UUID.randomUUID();
        publish(TaskChangedEvent.bookedWorkCreated(TASK_ID, PROJECT_ID, "Hours task",
                bookedId, USER_ID, WorkType.DEVELOPMENT, BigInteger.valueOf(4)));
        publish(TaskChangedEvent.bookedWorkUpdated(TASK_ID, PROJECT_ID, "Hours task",
                bookedId, USER_ID, WorkType.DEVELOPMENT, BigInteger.valueOf(7)));

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(plannedRepository.count()).isEqualTo(1);
            ReportBookedWorkRepository repo = bookedRepository;
            assertThat(repo.findAll()).hasSize(1);
            assertThat(repo.findAll().get(0).getBookedHours()).isEqualTo(7L);
        });

        List<TaskHoursResponse> byTask = get("/api/v1/reports/hours/by-task?projectId=" + PROJECT_ID,
                new ParameterizedTypeReference<>() {});
        assertThat(byTask).hasSize(1);
        assertThat(byTask.get(0).getPlannedHours()).isEqualTo(10L);
        assertThat(byTask.get(0).getBookedHours()).isEqualTo(7L);
        assertThat(byTask.get(0).getTaskCode()).isEqualTo("T-HRS");

        List<ProjectHoursResponse> byProject = get("/api/v1/reports/hours/by-project",
                new ParameterizedTypeReference<>() {});
        assertThat(byProject).anyMatch(p -> PROJECT_ID.equals(p.getProjectId())
                && p.getPlannedHours() == 10L && p.getBookedHours() == 7L);

        List<DetailedHoursResponse> detailed = get("/api/v1/reports/hours/detailed?taskId=" + TASK_ID,
                new ParameterizedTypeReference<>() {});
        assertThat(detailed).hasSize(1);
        assertThat(detailed.get(0).getUserId()).isEqualTo(USER_ID);
        assertThat(detailed.get(0).getWorkType()).isEqualTo(WorkType.DEVELOPMENT);
        assertThat(detailed.get(0).getPlannedHours()).isEqualTo(10L);
        assertThat(detailed.get(0).getBookedHours()).isEqualTo(7L);
    }

    @Test
    void bookedWorkDeleted_excludesRowFromAggregates() throws Exception {
        publish(TaskChangedEvent.plannedWorkCreated(TASK_ID, PROJECT_ID, "Hours task",
                UUID.randomUUID(), USER_ID, WorkType.DEVELOPMENT, BigInteger.valueOf(5)));

        UUID bookedId = UUID.randomUUID();
        publish(TaskChangedEvent.bookedWorkCreated(TASK_ID, PROJECT_ID, "Hours task",
                bookedId, USER_ID, WorkType.DEVELOPMENT, BigInteger.valueOf(3)));
        publish(TaskChangedEvent.bookedWorkDeleted(TASK_ID, PROJECT_ID, "Hours task", bookedId));

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(bookedRepository.findAll()).isEmpty());

        List<TaskHoursResponse> byTask = get("/api/v1/reports/hours/by-task",
                new ParameterizedTypeReference<>() {});
        assertThat(byTask).hasSize(1);
        assertThat(byTask.get(0).getPlannedHours()).isEqualTo(5L);
        assertThat(byTask.get(0).getBookedHours()).isZero();
    }

    @Test
    void duplicateBookedWorkEvent_upsertsOnce() throws Exception {
        UUID bookedId = UUID.randomUUID();
        // Same object → same eventId → second delivery must be skipped by dedup guard
        TaskChangedEvent event = TaskChangedEvent.bookedWorkCreated(TASK_ID, PROJECT_ID, "Hours task",
                bookedId, USER_ID, WorkType.DEVELOPMENT, BigInteger.valueOf(6));

        publish(event);
        publish(event);

        // pollDelay: the second delivery is silently discarded by dedup, so the
        // first Awaitility check would pass after only message #1 is processed —
        // leaving message #2 in the consumer pipeline and leaking into the next test.
        // Delay the first poll long enough for both events to be committed.
        Awaitility.await()
                .pollDelay(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    assertThat(bookedRepository.findAll()).hasSize(1);
                    assertThat(bookedRepository.findAll().get(0).getBookedHours()).isEqualTo(6L);
                    assertThat(processedEventRepository.count()).isEqualTo(1);
                });
    }

    private void publish(TaskChangedEvent event) throws Exception {
        kafkaTemplate.send(KafkaTopics.TASK_CHANGED, event.getTaskId().toString(),
                objectMapper.writeValueAsString(event));
    }

    private <T> T get(String url, ParameterizedTypeReference<T> type) {
        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, null, type);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
