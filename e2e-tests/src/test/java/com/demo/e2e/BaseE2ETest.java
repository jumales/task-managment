package com.demo.e2e;

import com.demo.audit.AuditServiceApplication;
import com.demo.audit.repository.AuditRepository;
import com.demo.audit.repository.CommentAuditRepository;
import com.demo.audit.repository.PhaseAuditRepository;
import com.demo.common.event.TaskChangedEvent;
import com.demo.e2e.config.E2ESecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

/**
 * Shared base for all e2e tests.
 *
 * <p>Starts a real audit-service Spring Boot context backed by Testcontainers
 * PostgreSQL and Kafka. Tests publish {@link TaskChangedEvent} messages directly
 * via the auto-configured {@code KafkaTemplate} and assert that the audit-service
 * Kafka consumer persists the correct records.
 *
 * <p>Containers are started once in a static initializer (not via {@code @Container})
 * so they are shared across all subclasses that cache the same Spring context.
 * {@code @DynamicPropertySource} injects consistent connection URLs into the context.
 */
@SpringBootTest(
        classes = {AuditServiceApplication.class, E2ESecurityConfig.class},
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                // Prevents JwtDecoder auto-config from fetching OpenID metadata at startup
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/jwks"
        }
)
@Import(E2ESecurityConfig.class)
abstract class BaseE2ETest {

    /** Shared Postgres container — started once for the entire e2e test suite. */
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /** Shared Kafka container — started once for the entire e2e test suite. */
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static {
        // Start both containers in parallel before any Spring context is created.
        // Without @Container, Testcontainers does not manage their lifecycle;
        // JVM shutdown hooks close them after all tests complete.
        Startables.deepStart(postgres, kafka).join();
    }

    /** Injects container URLs into the Spring context before it is created. */
    @DynamicPropertySource
    static void configureContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    protected KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    protected AuditRepository auditRepository;

    @Autowired
    protected CommentAuditRepository commentAuditRepository;

    @Autowired
    protected PhaseAuditRepository phaseAuditRepository;

    @Autowired
    protected TestRestTemplate restTemplate;

    @LocalServerPort
    protected int port;

    /** Task and user IDs are unique per test to isolate assertions. */
    protected UUID taskId;
    protected UUID userId;

    @BeforeEach
    void setUpIds() {
        taskId = UUID.randomUUID();
        userId = UUID.randomUUID();
        auditRepository.deleteAll();
        commentAuditRepository.deleteAll();
        phaseAuditRepository.deleteAll();
    }

    /** Publishes a {@link TaskChangedEvent} to the task-changed Kafka topic. */
    protected void publish(TaskChangedEvent event) {
        kafkaTemplate.send("task-changed", event.getTaskId().toString(), event);
    }

    /** Builds the base URL for HTTP assertions against the running audit-service. */
    protected String url(String path) {
        return "http://localhost:" + port + path;
    }
}
