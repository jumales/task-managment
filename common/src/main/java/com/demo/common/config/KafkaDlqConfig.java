package com.demo.common.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

/**
 * Shared Kafka DLQ configuration providing bounded retry and dead-letter forwarding.
 *
 * <p>Not auto-configured — each consuming service must {@code @Import} this class.
 * This prevents pulling Kafka beans into non-Kafka services (file-service, etc.).
 *
 * <p>Retry policy: 2 retries after the initial attempt with exponential backoff
 * starting at 1 s (multiplier 2.0). After exhaustion the failed record is forwarded
 * to the original topic name + {@code .DLT} (e.g., {@code task-changed.DLT}) and the
 * offset is committed so the consumer moves on.
 */
@Configuration
public class KafkaDlqConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * A dedicated KafkaTemplate whose value serializer is {@link ByteArraySerializer}.
     *
     * <p>{@link DeadLetterPublishingRecoverer} receives the raw bytes of the failed record
     * and must forward them as-is — using {@code ByteArraySerializer} avoids double-serialization.
     * Named {@code dltKafkaTemplate} to avoid conflicting with service-owned templates.
     */
    @Bean("dltKafkaTemplate")
    public KafkaTemplate<String, byte[]> dltKafkaTemplate() {
        Map<String, Object> props = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class
        );
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /**
     * Publishes the failed record verbatim to {@code <original-topic>.DLT}.
     * Spring Kafka appends the exception class, message, and cause as Kafka headers
     * — queryable with any Kafka consumer or inspection tool (e.g. Kafdrop).
     * The record's key, original bytes, and trace context headers are all preserved.
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            @Qualifier("dltKafkaTemplate") KafkaTemplate<String, byte[]> dltKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(dltKafkaTemplate);
    }

    /**
     * Error handler with exponential backoff and DLT recovery.
     *
     * <p>Retry schedule (3 total attempts):
     * <ul>
     *   <li>Attempt 1: immediate (original poll)</li>
     *   <li>Attempt 2: after ~1 s</li>
     *   <li>Attempt 3: after ~2 s</li>
     *   <li>Exhausted: forward to DLT, commit offset, continue</li>
     * </ul>
     *
     * <p>{@link DeserializationException} is non-retryable — malformed bytes will never
     * deserialize successfully, so they go straight to DLT on the first failure.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(2); // 2 retries → 3 total attempts

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }
}
