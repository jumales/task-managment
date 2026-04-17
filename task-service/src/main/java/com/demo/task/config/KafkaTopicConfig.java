package com.demo.task.config;

import com.demo.common.config.KafkaTopics;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

import java.time.Duration;

/**
 * Declares Kafka topics with configurable retention so Spring's {@code KafkaAdmin}
 * creates or updates them on startup. Uses {@link NewTopics} to register both topics
 * in a single bean, keeping the config self-contained.
 */
@Configuration
public class KafkaTopicConfig {

    private final TtlProperties ttlProperties;

    public KafkaTopicConfig(TtlProperties ttlProperties) {
        this.ttlProperties = ttlProperties;
    }

    /**
     * Ensures both task topics exist with the configured retention period.
     * If the topics already exist only the retention config is updated.
     */
    @Bean
    public NewTopics taskTopics() {
        long taskEventsRetentionMs = Duration.ofHours(
                ttlProperties.getKafka().getTaskEventsRetentionHours()).toMillis();
        long taskChangedRetentionMs = Duration.ofHours(
                ttlProperties.getKafka().getTaskChangedRetentionHours()).toMillis();

        return new NewTopics(
                TopicBuilder.name(KafkaTopics.TASK_EVENTS)
                        .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(taskEventsRetentionMs))
                        .build(),
                TopicBuilder.name(KafkaTopics.TASK_CHANGED)
                        .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(taskChangedRetentionMs))
                        .build()
        );
    }
}
