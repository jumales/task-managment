package com.demo.notification.service;

import com.demo.common.dto.ProjectNotificationTemplateResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangedEvent;
import com.demo.notification.client.TaskServiceClient;
import com.demo.notification.client.UserClientHelper;
import com.demo.common.dto.PageResponse;
import com.demo.notification.dto.NotificationResponse;
import com.demo.notification.model.NotificationRecord;
import com.demo.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    private final UserClientHelper userClientHelper;
    private final EmailService emailService;
    private final TaskServiceClient taskServiceClient;

    /** Base URL of the frontend application, used to build the {taskUrl} placeholder value. */
    private final String frontendUrl;

    public NotificationService(NotificationRepository repository,
                                UserClientHelper userClientHelper,
                                EmailService emailService,
                                TaskServiceClient taskServiceClient,
                                @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.repository = repository;
        this.userClientHelper = userClientHelper;
        this.emailService = emailService;
        this.taskServiceClient = taskServiceClient;
        this.frontendUrl = frontendUrl;
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

        UserDto user = userClientHelper.getUserById(recipientId);
        if (user == null) {
            log.warn("Cannot resolve user {} (user-service unavailable) — skipping notification", recipientId);
            return;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("User {} has no email address — skipping notification", recipientId);
            return;
        }

        String[] content = buildEmailContent(event, user.getName());
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

    /** Returns a paginated page of sent notifications for a given task, ordered by send time. */
    public PageResponse<NotificationResponse> getByTaskId(UUID taskId, Pageable pageable) {
        Page<NotificationRecord> page = repository.findByTaskIdOrderBySentAtAsc(taskId, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    /** Planned/booked-work events notify the work entry's user; all other events notify the task's assignee. */
    private UUID resolveRecipientId(TaskChangedEvent event) {
        return switch (event.getChangeType()) {
            case PLANNED_WORK_CREATED,
                 BOOKED_WORK_CREATED,
                 BOOKED_WORK_UPDATED,
                 BOOKED_WORK_DELETED -> event.getWorkLogUserId();
            default -> event.getAssignedUserId();
        };
    }

    /**
     * Returns the email [subject, body] for the event.
     * Applies a project-level template if one is configured; otherwise falls back to built-in defaults.
     *
     * @param userName full name of the notification recipient, used for the {userName} placeholder
     * @return two-element array: [subject, body]
     */
    private String[] buildEmailContent(TaskChangedEvent event, String userName) {
        if (event.getProjectId() == null) return buildDefaultContent(event);
        String[] templated = applyProjectTemplate(event, userName);
        if (templated != null) return templated;
        return buildDefaultContent(event);
    }

    /**
     * Tries to fetch and render the project-level template for this event type.
     * Returns null if no template is configured or the call fails.
     */
    private String[] applyProjectTemplate(TaskChangedEvent event, String userName) {
        try {
            ProjectNotificationTemplateResponse template =
                    taskServiceClient.getTemplate(event.getProjectId(), event.getChangeType());
            Map<String, String> vars = buildTemplateVars(event, userName);
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

    /**
     * Builds the variable map for placeholder substitution.
     * Contains all tokens defined in {@link com.demo.common.dto.TemplatePlaceholder}.
     *
     * @param userName full name of the notification recipient
     */
    private Map<String, String> buildTemplateVars(TaskChangedEvent event, String userName) {
        return Map.ofEntries(
                Map.entry("taskId",       safeStr(event.getTaskId())),
                Map.entry("taskTitle",    safeStr(event.getTaskTitle())),
                Map.entry("taskUrl",      frontendUrl + "/tasks/" + safeStr(event.getTaskId())),
                Map.entry("projectId",    safeStr(event.getProjectId())),
                Map.entry("userName",     safeStr(userName)),
                Map.entry("fromStatus",   safeStr(event.getFromStatus())),
                Map.entry("toStatus",     safeStr(event.getToStatus())),
                Map.entry("comment",      safeStr(event.getCommentContent())),
                Map.entry("fromPhase",    safeStr(event.getFromPhaseName())),
                Map.entry("toPhase",      safeStr(event.getToPhaseName())),
                Map.entry("workType",     safeStr(event.getWorkType())),
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
            case PLANNED_WORK_CREATED -> new String[]{
                "Planned work added to task",
                "Planned work of type " + event.getWorkType() + " was added to task '"
                    + title + "' with " + event.getPlannedHours() + " planned hours."
            };
            case BOOKED_WORK_CREATED -> new String[]{
                "Booked work added to task",
                "Booked work of type " + event.getWorkType() + " was added to task '"
                    + title + "' with " + event.getBookedHours() + " booked hours."
            };
            case BOOKED_WORK_UPDATED -> new String[]{
                "Booked work updated on task",
                "Booked work of type " + event.getWorkType() + " on task '" + title
                    + "' was updated. Booked hours: " + event.getBookedHours() + "."
            };
            case BOOKED_WORK_DELETED -> new String[]{
                "Booked work removed from task",
                "A booked-work entry was removed from task '" + title + "'."
            };
            case ATTACHMENT_ADDED -> new String[]{
                "Attachment added to task",
                "A file '" + event.getFileName() + "' was attached to task '" + title + "'."
            };
            case ATTACHMENT_DELETED -> new String[]{
                "Attachment removed from task",
                "The file '" + event.getFileName() + "' was removed from task '" + title + "'."
            };
            case TASK_UPDATED -> new String[]{
                "Task updated: " + title,
                "Task '" + title + "' has been updated."
            };
            case PARTICIPANT_ADDED -> new String[]{
                "You are now watching task: " + title,
                "You have been added as a participant on task '" + title + "'."
            };
            case PARTICIPANT_REMOVED -> new String[]{
                "You have been removed from task: " + title,
                "You have been removed as a participant from task '" + title + "'."
            };
            case TIMELINE_CHANGED -> new String[]{
                "Timeline updated on task: " + title,
                "A timeline date was changed on task '" + title + "'."
            };
            // System event — no user-facing email; exists only to trigger the WebSocket push.
            // resolveRecipientId returns assignedUserId which may send an email to the assignee,
            // but the primary purpose is the real-time frontend update.
            case TASK_CODE_ASSIGNED -> new String[]{
                "Task code assigned: " + title,
                "Task '" + title + "' has been assigned a task code."
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
