package com.demo.common.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Shared Kafka DLQ configuration providing bounded retry and dead-letter forwarding.
 *
 * <p>Not auto-configured — each consuming service must {@code @Import} this class via its own
 * {@code @Configuration} class. Without {@code @Configuration} on this class, component scanning
 * skips it entirely, preventing interference with Spring Boot's auto-configured
 * {@code KafkaTemplate} in producer-only services (e.g. task-service).
 *
 * <p>The DLT {@link KafkaTemplate} is intentionally NOT registered as a Spring bean.
 * Registering any {@code KafkaTemplate} bean would satisfy Spring Boot's
 * {@code @ConditionalOnMissingBean(KafkaOperations.class)} guard and prevent the default
 * {@code KafkaTemplate} from being auto-configured in consuming services.
 *
 * <p>Retry policy: 2 retries after the initial attempt with exponential backoff
 * starting at 1 s (multiplier 2.0). After exhaustion the failed record is forwarded
 * to the original topic name + {@code .DLT} (e.g., {@code task-changed.DLT}) and the
 * offset is committed so the consumer moves on.
 */
public class KafkaDlqConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Publishes the failed record verbatim to {@code <original-topic>.DLT}.
     * Spring Kafka appends the exception class, message, and cause as Kafka headers
     * — queryable with any Kafka consumer or inspection tool (e.g. Kafdrop).
     * The record's key, original bytes, and trace context headers are all preserved.
     *
     * <p>The {@link KafkaTemplate} used here is created inline (not a bean) to avoid
     * triggering Spring Boot's {@code @ConditionalOnMissingBean(KafkaOperations.class)}
     * guard, which would prevent auto-configuration of the service's own template.
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer() {
        Map<String, Object> props = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class
        );
        KafkaTemplate<String, byte[]> dltTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        return new DeadLetterPublishingRecoverer(dltTemplate);
    }

    /**
     * Injects MDC context into Kafka listener threads before each record is processed.
     * Extracts {@code correlationId} from the record header if present; falls back to a fresh UUID.
     * Clears MDC after each record to prevent context leaking into the next record's thread.
     */
    @Bean
    public RecordInterceptor<Object, Object> mdcRecordInterceptor() {
        return new RecordInterceptor<>() {
            private static final String HEADER_CORRELATION_ID = "correlationId";

            @Override
            public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                            Consumer<Object, Object> consumer) {
                Header header = record.headers().lastHeader(HEADER_CORRELATION_ID);
                String correlationId = header != null
                        ? new String(header.value(), StandardCharsets.UTF_8)
                        : UUID.randomUUID().toString();
                MDC.put("requestId", correlationId);
                MDC.put("kafkaTopic", record.topic());
                MDC.put("kafkaPartition", String.valueOf(record.partition()));
                return record;
            }

            @Override
            public void afterRecord(ConsumerRecord<Object, Object> record,
                                    Consumer<Object, Object> consumer) {
                MDC.clear();
            }
        };
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
