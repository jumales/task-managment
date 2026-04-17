package com.demo.notification.config;

import com.demo.common.config.KafkaDlqConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * Wires the shared DLQ error handler into the Kafka listener container factory.
 * Replaces the MANUAL_IMMEDIATE ack pattern with framework-managed RECORD acks,
 * enabling bounded retry and dead-letter forwarding via {@link KafkaDlqConfig}.
 */
@Configuration
@Import(KafkaDlqConfig.class)
public class KafkaConsumerConfig {

    /**
     * Overrides Spring Boot's default factory to register the {@link DefaultErrorHandler}
     * with exponential backoff and DLT recovery. Sets ack mode to RECORD so the framework
     * commits offsets after each successful record (or after DLT forwarding on exhaustion).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        return factory;
    }
}
