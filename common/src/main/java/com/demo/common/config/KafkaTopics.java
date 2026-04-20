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

    /** Dead-letter topic for unprocessable task-changed events (after all retries exhausted). */
    public static final String TASK_CHANGED_DLT = TASK_CHANGED + ".DLT";

    /** Dead-letter topic for unprocessable task-events events. */
    public static final String TASK_EVENTS_DLT = TASK_EVENTS + ".DLT";

    /** Dead-letter topic for unprocessable user-events events. */
    public static final String USER_EVENTS_DLT = USER_EVENTS + ".DLT";

    /**
     * Consumer group IDs for DLT topics. Used by DLQ monitoring (consumer lag = end_offset - committed_offset)
     * and by any DLT replay consumer. Until a DLT consumer commits offsets, lag equals the end offset —
     * every dead-lettered message is treated as unprocessed.
     */
    public static final String TASK_CHANGED_DLT_GROUP = "dlt-task-changed-group";
    public static final String TASK_EVENTS_DLT_GROUP  = "dlt-task-events-group";
    public static final String USER_EVENTS_DLT_GROUP  = "dlt-user-events-group";

    private KafkaTopics() {}
}
