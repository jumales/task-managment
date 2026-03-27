package com.demo.common.event;

/** Discriminator for task lifecycle events published to {@code task-events} Kafka topic. */
public enum TaskEventType {
    CREATED,
    UPDATED,
    DELETED
}
