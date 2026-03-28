package com.demo.notification.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import com.demo.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for task change events.
 * Delegates each event to {@link NotificationService} to trigger the appropriate email notification.
 */
@Component
public class TaskEventNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventNotificationConsumer.class);

    private final NotificationService notificationService;

    public TaskEventNotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Receives a task change event from Kafka and triggers an email notification. */
    @KafkaListener(topics = KafkaTopics.TASK_CHANGED, groupId = "notification-group", concurrency = "3")
    public void consume(TaskChangedEvent event) {
        log.info("Received TaskChangedEvent: task={} changeType={}", event.getTaskId(), event.getChangeType());
        notificationService.notify(event);
    }
}
