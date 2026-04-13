package com.demo.notification.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import com.demo.notification.service.NotificationService;
import com.demo.notification.service.TaskPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for task change events.
 * Delegates each event to {@link NotificationService} to trigger the appropriate email notification.
 */
@Component
public class TaskEventNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventNotificationConsumer.class);

    private final NotificationService notificationService;
    private final TaskPushService taskPushService;

    public TaskEventNotificationConsumer(NotificationService notificationService,
                                         TaskPushService taskPushService) {
        this.notificationService = notificationService;
        this.taskPushService = taskPushService;
    }

    /** Receives a task change event from Kafka, triggers an email notification, and pushes via WebSocket. */
    @KafkaListener(topics = KafkaTopics.TASK_CHANGED, groupId = "notification-group", concurrency = "3")
    public void consume(TaskChangedEvent event, Acknowledgment ack) {
        log.info("Received TaskChangedEvent: task={} changeType={}", event.getTaskId(), event.getChangeType());
        try {
            notificationService.notify(event);
            // notify() is @Async so this executes immediately in the consumer thread — no blocking risk
            taskPushService.push(event.getTaskId(), event.getChangeType());
            ack.acknowledge(); // commit offset only after successful processing
        } catch (Exception e) {
            log.error("Failed to process event {}: {}", event.getTaskId(), e.getMessage(), e);
            // Do not acknowledge — offset not committed, message will be retried
        }
    }
}
