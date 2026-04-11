package com.demo.notification.service;

import com.demo.common.event.TaskChangeType;
import com.demo.common.event.TaskPushMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Pushes task change signals to all STOMP clients subscribed to {@code /topic/tasks/{taskId}}.
 * Invoked by the Kafka consumer on every {@code TaskChangedEvent} so that browser tabs currently
 * viewing a task detail page receive the signal and can re-fetch only the changed sub-resource.
 */
@Service
public class TaskPushService {

    private static final String TOPIC_PREFIX = "/topic/tasks/";

    private final SimpMessagingTemplate messagingTemplate;

    public TaskPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends a {@link TaskPushMessage} to {@code /topic/tasks/{taskId}}.
     * No-op when {@code taskId} is null (e.g. events where only workLogUserId is set).
     */
    public void push(UUID taskId, TaskChangeType changeType) {
        if (taskId == null) return;
        messagingTemplate.convertAndSend(TOPIC_PREFIX + taskId, new TaskPushMessage(taskId, changeType));
    }
}
