package com.demo.audit;

import com.demo.audit.health.DltHealthIndicator;
import com.demo.common.config.KafkaTopics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies {@link DltHealthIndicator} reports UP when all DLTs are empty and DOWN after a
 * message lands on a DLT. DLT consumer group has no committed offset, so lag equals end offset.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class DltHealthIndicatorIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    DltHealthIndicator healthIndicator;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void reportsUpWhenAllDltsAreEmpty() {
        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKeys(
                KafkaTopics.TASK_CHANGED_DLT,
                KafkaTopics.TASK_EVENTS_DLT,
                KafkaTopics.USER_EVENTS_DLT
        );
        assertThat(health.getDetails().values()).allMatch(lag -> lag.equals(0L));
    }

    @Test
    void reportsDownWhenMessageLandsOnDlt() throws Exception {
        kafkaTemplate.send(KafkaTopics.TASK_CHANGED_DLT, "k", "payload").get(10, SECONDS);

        await().atMost(20, SECONDS).untilAsserted(() -> {
            Health health = healthIndicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat((Long) health.getDetails().get(KafkaTopics.TASK_CHANGED_DLT)).isGreaterThan(0L);
            assertThat(health.getDetails().get("message"))
                    .isEqualTo("Dead-letter topics have unconsumed messages");
        });
    }
}
