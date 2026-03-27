package com.demo.common.config;

/**
 * Central registry of Kafka topic names shared across services.
 * Use these constants in both producers ({@code @KafkaListener}) and consumers
 * to ensure topic names stay in sync without string duplication.
 */
public final class KafkaTopics {

    /** Topic for all task change events (status, comment, phase). Consumed by audit-service. */
    public static final String TASK_CHANGED = "task-changed";

    /** Topic for task lifecycle events (created, updated, deleted). Consumed by search-service. */
    public static final String TASK_EVENTS = "task-events";

    /** Topic for user lifecycle events (created, updated, deleted). Consumed by search-service. */
    public static final String USER_EVENTS = "user-events";

    private KafkaTopics() {}
}
