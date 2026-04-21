package com.demo.notification.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import com.demo.notification.dedup.ProcessedEventService;
import com.demo.notification.service.NotificationService;
import com.demo.notification.service.TaskPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for task change events.
 * Delegates each event to {@link NotificationService} to trigger the appropriate email notification.
 * Exceptions propagate to {@code DefaultErrorHandler} for bounded retry and DLT forwarding.
 */
@Component
public class TaskEventNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventNotificationConsumer.class);

    /** Kafka consumer group — paired with the {@link #consume} @KafkaListener groupId. */
    private static final String CONSUMER_GROUP = "notification-group";

    private final NotificationService notificationService;
    private final TaskPushService taskPushService;
    private final ProcessedEventService processedEventService;

    public TaskEventNotificationConsumer(NotificationService notificationService,
                                         TaskPushService taskPushService,
                                         ProcessedEventService processedEventService) {
        this.notificationService = notificationService;
        this.taskPushService = taskPushService;
        this.processedEventService = processedEventService;
    }

    /** Receives a task change event from Kafka, triggers an email notification, and pushes via WebSocket. */
    @KafkaListener(topics = KafkaTopics.TASK_CHANGED, groupId = CONSUMER_GROUP, concurrency = "3")
    public void consume(TaskChangedEvent event) {
        log.info("Received TaskChangedEvent: task={} changeType={}", event.getTaskId(), event.getChangeType());
        if (!processedEventService.markProcessed(event.getEventId(), CONSUMER_GROUP)) {
            log.info("Duplicate event {} — skipping", event.getEventId());
            return;
        }
        notificationService.notify(event);
        // notify() is @Async so this executes immediately in the consumer thread — no blocking risk
        taskPushService.push(event.getTaskId(), event.getChangeType());
    }
}
