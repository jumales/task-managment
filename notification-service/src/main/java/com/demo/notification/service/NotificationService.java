package com.demo.notification.service;

import com.demo.common.dto.ProjectNotificationTemplateResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangedEvent;
import com.demo.notification.client.TaskServiceClient;
import com.demo.notification.client.UserClient;
import com.demo.notification.dto.NotificationResponse;
import com.demo.notification.model.NotificationRecord;
import com.demo.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates email notifications for task change events.
 * Resolves the recipient user, applies the project-specific template if configured
 * (falling back to built-in defaults), sends the mail, and persists a record for history.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final UserClient userClient;
    private final EmailService emailService;
    private final TaskServiceClient taskServiceClient;

    public NotificationService(NotificationRepository repository,
                                UserClient userClient,
                                EmailService emailService,
                                TaskServiceClient taskServiceClient) {
        this.repository = repository;
        this.userClient = userClient;
        this.emailService = emailService;
        this.taskServiceClient = taskServiceClient;
    }

    /**
     * Processes a task change event asynchronously: resolves the recipient, applies any
     * project-level template, sends an email, and stores the notification record.
     * Skips silently if no recipient is set.
     * Runs in a separate thread so the Kafka consumer is not blocked by the email send.
     */
    @Async
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
     * Returns the email [subject, body] for the event.
     * Applies a project-level template if one is configured; otherwise falls back to built-in defaults.
     *
     * @return two-element array: [subject, body]
     */
    private String[] buildEmailContent(TaskChangedEvent event) {
        if (event.getProjectId() != null) {
            String[] templated = applyProjectTemplate(event);
            if (templated != null) return templated;
        }
        return buildDefaultContent(event);
    }

    /**
     * Tries to fetch and render the project-level template for this event type.
     * Returns null if no template is configured or the call fails.
     */
    private String[] applyProjectTemplate(TaskChangedEvent event) {
        try {
            ProjectNotificationTemplateResponse template =
                    taskServiceClient.getTemplate(event.getProjectId(), event.getChangeType());
            Map<String, String> vars = buildTemplateVars(event);
            return new String[]{
                renderTemplate(template.getSubjectTemplate(), vars),
                renderTemplate(template.getBodyTemplate(), vars)
            };
        } catch (Exception e) {
            // 404 (no template configured) or any connectivity issue — fall through to defaults
            log.debug("No project template for project {} event {} — using default",
                    event.getProjectId(), event.getChangeType());
            return null;
        }
    }

    /** Replaces {placeholder} tokens in the template string with values from the map. */
    private String renderTemplate(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    /** Builds the variable map available for template placeholder substitution. */
    private Map<String, String> buildTemplateVars(TaskChangedEvent event) {
        return Map.ofEntries(
                Map.entry("taskId",      safeStr(event.getTaskId())),
                Map.entry("taskTitle",   safeStr(event.getTaskTitle())),
                Map.entry("projectId",   safeStr(event.getProjectId())),
                Map.entry("fromStatus",  safeStr(event.getFromStatus())),
                Map.entry("toStatus",    safeStr(event.getToStatus())),
                Map.entry("comment",     safeStr(event.getCommentContent())),
                Map.entry("fromPhase",   safeStr(event.getFromPhaseName())),
                Map.entry("toPhase",     safeStr(event.getToPhaseName())),
                Map.entry("workType",    safeStr(event.getWorkType())),
                Map.entry("plannedHours", safeStr(event.getPlannedHours())),
                Map.entry("bookedHours",  safeStr(event.getBookedHours()))
        );
    }

    private String safeStr(Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Builds the default email subject and body for the given event type.
     *
     * @return two-element array: [subject, body]
     */
    private String[] buildDefaultContent(TaskChangedEvent event) {
        String title = event.getTaskTitle() != null ? event.getTaskTitle() : event.getTaskId().toString();
        return switch (event.getChangeType()) {
            case TASK_CREATED -> new String[]{
                "New task assigned: " + title,
                "A new task '" + title + "' has been created and assigned to you."
            };
            case STATUS_CHANGED -> new String[]{
                "Task status changed to " + event.getToStatus(),
                "The status of task '" + title + "' has changed from "
                    + event.getFromStatus() + " to " + event.getToStatus() + "."
            };
            case COMMENT_ADDED -> new String[]{
                "New comment on your task",
                "A new comment was added to task '" + title + "':\n\n" + event.getCommentContent()
            };
            case PHASE_CHANGED -> new String[]{
                "Task moved to phase '" + event.getToPhaseName() + "'",
                "Task '" + title + "' has moved from phase '" + event.getFromPhaseName()
                    + "' to '" + event.getToPhaseName() + "'."
            };
            case WORK_LOG_CREATED -> new String[]{
                "Work log added to your task",
                "A new work log of type " + event.getWorkType() + " was added to task '"
                    + title + "' with " + event.getPlannedHours() + " planned hours."
            };
            case WORK_LOG_UPDATED -> new String[]{
                "Work log updated on your task",
                "A work log of type " + event.getWorkType() + " on task '" + title
                    + "' was updated. Booked hours: " + event.getBookedHours() + "."
            };
            case WORK_LOG_DELETED -> new String[]{
                "Work log removed from your task",
                "A work log was removed from task '" + title + "'."
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
