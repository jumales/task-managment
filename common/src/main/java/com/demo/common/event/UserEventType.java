package com.demo.common.event;

/** Discriminator for user lifecycle events published to {@code user-events} Kafka topic. */
public enum UserEventType {
    CREATED,
    UPDATED,
    DELETED
}
