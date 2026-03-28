package com.demo.notification.service;

import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangedEvent;
import com.demo.notification.client.UserClient;
import com.demo.notification.dto.NotificationResponse;
import com.demo.notification.model.NotificationRecord;
import com.demo.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates email notifications for task change events.
 * Resolves the recipient user, builds email content, sends the mail,
 * and persists a record for history purposes.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final UserClient userClient;
    private final EmailService emailService;

    public NotificationService(NotificationRepository repository,
                                UserClient userClient,
                                EmailService emailService) {
        this.repository = repository;
        this.userClient = userClient;
        this.emailService = emailService;
    }

    /**
     * Processes a task change event: resolves the recipient, sends an email,
     * and stores the notification record. Skips silently if no recipient is set.
     */
    public void notify(TaskChangedEvent event) {
        UUID recipientId = resolveRecipientId(event);
        if (recipientId == null) {
            log.debug("No recipient for event {} on task {} — skipping", event.getChangeType(), event.getTaskId());
            return;
        }

        UserDto user = userClient.getUserById(recipientId);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("User {} has no email address — skipping notification", recipientId);
            return;
        }

        String[] content = buildEmailContent(event);
        String subject = content[0];
        String body    = content[1];

        emailService.send(user.getEmail(), subject, body);

        repository.save(NotificationRecord.builder()
                .taskId(event.getTaskId())
                .recipientUserId(recipientId)
                .recipientEmail(user.getEmail())
                .changeType(event.getChangeType())
                .subject(subject)
                .body(body)
                .sentAt(Instant.now())
                .build());
    }

    /** Returns all sent notifications for a given task, ordered by send time. */
    public List<NotificationResponse> getByTaskId(UUID taskId) {
        return repository.findByTaskIdOrderBySentAtAsc(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Work-log events notify the work-log's user; all other events notify the task's assignee. */
    private UUID resolveRecipientId(TaskChangedEvent event) {
        return switch (event.getChangeType()) {
            case WORK_LOG_CREATED, WORK_LOG_UPDATED, WORK_LOG_DELETED -> event.getWorkLogUserId();
            default -> event.getAssignedUserId();
        };
    }

    /**
     * Builds the email subject and body for the given event.
     *
     * @return two-element array: [subject, body]
     */
    private String[] buildEmailContent(TaskChangedEvent event) {
        return switch (event.getChangeType()) {
            case STATUS_CHANGED -> new String[]{
                "Task status changed to " + event.getToStatus(),
                "The status of task " + event.getTaskId() + " has changed from "
                    + event.getFromStatus() + " to " + event.getToStatus() + "."
            };
            case COMMENT_ADDED -> new String[]{
                "New comment on your task",
                "A new comment was added to task " + event.getTaskId() + ":\n\n" + event.getCommentContent()
            };
            case PHASE_CHANGED -> new String[]{
                "Task moved to phase '" + event.getToPhaseName() + "'",
                "Task " + event.getTaskId() + " has moved from phase '" + event.getFromPhaseName()
                    + "' to '" + event.getToPhaseName() + "'."
            };
            case WORK_LOG_CREATED -> new String[]{
                "Work log added to your task",
                "A new work log of type " + event.getWorkType() + " was added to task "
                    + event.getTaskId() + " with " + event.getPlannedHours() + " planned hours."
            };
            case WORK_LOG_UPDATED -> new String[]{
                "Work log updated on your task",
                "A work log of type " + event.getWorkType() + " on task " + event.getTaskId()
                    + " was updated. Booked hours: " + event.getBookedHours() + "."
            };
            case WORK_LOG_DELETED -> new String[]{
                "Work log removed from your task",
                "A work log was removed from task " + event.getTaskId() + "."
            };
        };
    }

    private NotificationResponse toResponse(NotificationRecord record) {
        return new NotificationResponse(
                record.getId(),
                record.getTaskId(),
                record.getRecipientUserId(),
                record.getRecipientEmail(),
                record.getChangeType(),
                record.getSubject(),
                record.getBody(),
                record.getSentAt()
        );
    }
}
