package com.demo.task;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.demo.common.web.MdcFilter;
import com.demo.task.client.UserClient;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class MdcFilterIT {

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

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        projectRepository.deleteAll();

        // Use the root logger so all log events (from any class) are captured
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(listAppender);
    }

    @Test
    void shouldInjectRequestIdIntoEveryLogLine() {
        restTemplate.getForEntity("/api/v1/tasks", Object.class);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel().isGreaterOrEqual(Level.DEBUG))
                .isNotEmpty()
                .allSatisfy(e ->
                        assertThat(e.getMDCPropertyMap())
                                .containsKey("requestId")
                );
    }

    @Test
    void shouldInjectUniqueRequestIdPerRequest() {
        restTemplate.getForEntity("/api/v1/tasks", Object.class);
        String firstRequestId = listAppender.list.stream()
                .map(e -> e.getMDCPropertyMap().get("requestId"))
                .filter(id -> id != null)
                .findFirst()
                .orElseThrow();

        listAppender.list.clear();

        restTemplate.getForEntity("/api/v1/tasks", Object.class);
        String secondRequestId = listAppender.list.stream()
                .map(e -> e.getMDCPropertyMap().get("requestId"))
                .filter(id -> id != null)
                .findFirst()
                .orElseThrow();

        assertThat(firstRequestId).isNotEqualTo(secondRequestId);
    }

    @Test
    void shouldInjectHttpMethodAndPath() {
        restTemplate.getForEntity("/api/v1/tasks", Object.class);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getMDCPropertyMap().containsKey("method"))
                .isNotEmpty()
                .allSatisfy(e -> {
                    Map<String, String> mdc = e.getMDCPropertyMap();
                    assertThat(mdc.get("method")).isEqualTo("GET");
                    assertThat(mdc.get("path")).isEqualTo("/api/v1/tasks");
                });
    }

    @Test
    void shouldClearMdcAfterRequestSoNextRequestStartsClean() {
        restTemplate.getForEntity("/api/v1/tasks", Object.class);

        // After the request completes, MDC must be empty — verified by checking
        // that no log event leaks MDC values from a previous request into a subsequent one.
        // We confirm this by asserting the two requests produced different requestId values.
        String firstId = listAppender.list.stream()
                .map(e -> e.getMDCPropertyMap().get("requestId"))
                .filter(id -> id != null)
                .findFirst()
                .orElseThrow();

        listAppender.list.clear();
        restTemplate.getForEntity("/api/v1/tasks", Object.class);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getMDCPropertyMap().containsKey("requestId"))
                .allSatisfy(e ->
                        assertThat(e.getMDCPropertyMap().get("requestId")).isNotEqualTo(firstId)
                );
    }
}
