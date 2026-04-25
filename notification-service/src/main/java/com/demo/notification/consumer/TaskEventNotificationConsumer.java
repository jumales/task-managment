package com.demo.notification.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.dto.TaskParticipantResponse;
import com.demo.common.event.TaskChangedEvent;
import com.demo.notification.client.TaskServiceClient;
import com.demo.notification.dedup.ProcessedEventService;
import com.demo.notification.service.FcmPushService;
import com.demo.notification.service.NotificationService;
import com.demo.notification.service.TaskPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Kafka consumer for task change events.
 * Triggers email, WebSocket, and FCM push notifications for each event.
 * Exceptions propagate to {@code DefaultErrorHandler} for bounded retry and DLT forwarding.
 */
@Component
public class TaskEventNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventNotificationConsumer.class);

    /** Kafka consumer group — paired with the {@link #consume} @KafkaListener groupId. */
    private static final String CONSUMER_GROUP = "notification-group";

    private final NotificationService notificationService;
    private final TaskPushService taskPushService;
    private final FcmPushService fcmPushService;
    private final TaskServiceClient taskServiceClient;
    private final ProcessedEventService processedEventService;

    public TaskEventNotificationConsumer(NotificationService notificationService,
                                         TaskPushService taskPushService,
                                         FcmPushService fcmPushService,
                                         TaskServiceClient taskServiceClient,
                                         ProcessedEventService processedEventService) {
        this.notificationService = notificationService;
        this.taskPushService = taskPushService;
        this.fcmPushService = fcmPushService;
        this.taskServiceClient = taskServiceClient;
        this.processedEventService = processedEventService;
    }

    /** Receives a task change event from Kafka, triggers email + WebSocket + FCM push notifications. */
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
        // FCM is also @Async — runs in its own thread pool, does not block the consumer
        fcmPushService.notifyUsers(
                resolveAffectedUsers(event), event.getChangeType(), event.getTaskId(), event.getProjectId());
    }

    /**
     * Resolves the set of user IDs that should receive an FCM push.
     * Includes the assignee, work-log user, and all task participants (watchers/joiners).
     * Falls back gracefully if task-service is unavailable.
     */
    private Set<UUID> resolveAffectedUsers(TaskChangedEvent event) {
        Set<UUID> userIds = new HashSet<>();
        if (event.getAssignedUserId() != null) userIds.add(event.getAssignedUserId());
        if (event.getWorkLogUserId() != null) userIds.add(event.getWorkLogUserId());
        addParticipants(userIds, event.getTaskId());
        return userIds;
    }

    private void addParticipants(Set<UUID> userIds, UUID taskId) {
        if (taskId == null) return;
        try {
            taskServiceClient.getParticipants(taskId).stream()
                    .map(TaskParticipantResponse::getUserId)
                    .forEach(userIds::add);
        } catch (Exception e) {
            // Degraded: task-service unavailable; push only to assignee/work-log user.
            log.warn("Could not resolve participants for task {} — FCM limited to direct recipients: {}",
                    taskId, e.getMessage());
        }
    }
}
