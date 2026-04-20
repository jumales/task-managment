package com.demo.audit;

import com.demo.audit.metrics.DltMetricsPublisher;
import com.demo.common.config.KafkaTopics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
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
 * Verifies {@link DltMetricsPublisher} registers one gauge per DLT topic and that
 * {@code refresh()} pulls current lag into the gauge values.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
@Import(TestSecurityConfig.class)
class DltMetricsPublisherIT {

    private static final String GAUGE_NAME = "kafka.dlt.consumer.lag";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    DltMetricsPublisher metricsPublisher;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void registersGaugePerDltTopic() {
        assertThat(gaugeFor(KafkaTopics.TASK_CHANGED_DLT)).isNotNull();
        assertThat(gaugeFor(KafkaTopics.TASK_EVENTS_DLT)).isNotNull();
        assertThat(gaugeFor(KafkaTopics.USER_EVENTS_DLT)).isNotNull();
    }

    @Test
    void gaugeReflectsLagAfterRefresh() throws Exception {
        kafkaTemplate.send(KafkaTopics.USER_EVENTS_DLT, "k", "payload").get(10, SECONDS);

        await().atMost(20, SECONDS).untilAsserted(() -> {
            metricsPublisher.refresh();
            Gauge gauge = gaugeFor(KafkaTopics.USER_EVENTS_DLT);
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isGreaterThan(0.0);
        });
    }

    private Gauge gaugeFor(String topic) {
        return meterRegistry.find(GAUGE_NAME).tag("topic", topic).gauge();
    }
}
