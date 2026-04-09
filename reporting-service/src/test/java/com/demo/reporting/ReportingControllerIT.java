package com.demo.reporting;

import com.demo.common.config.KafkaTopics;
import com.demo.common.dto.TaskStatus;
import com.demo.common.event.TaskEvent;
import com.demo.reporting.dto.MyTaskResponse;
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

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
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
}
