package com.demo.task.service;

import com.demo.common.dto.TaskAttachmentRequest;
import com.demo.common.dto.TaskAttachmentResponse;
import com.demo.common.dto.TaskParticipantRole;
import com.demo.common.event.TaskChangedEvent;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.FileClient;
import com.demo.task.client.UserClientHelper;
import com.demo.task.model.Task;
import com.demo.task.model.TaskAttachment;
import com.demo.task.outbox.OutboxWriter;
import com.demo.task.repository.TaskAttachmentRepository;
import com.demo.task.repository.TaskRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages file attachments on tasks.
 * Files are stored in file-service/MinIO; this service tracks metadata and
 * publishes ATTACHMENT_ADDED / ATTACHMENT_DELETED outbox events for audit consumers.
 */
@Service
public class TaskAttachmentService {

    private static final String ENTITY_NAME = "TaskAttachment";

    private final TaskAttachmentRepository repository;
    private final TaskRepository taskRepository;
    private final FileClient fileClient;
    private final UserClientHelper userClientHelper;
    private final OutboxWriter outboxWriter;
    private final TaskParticipantService participantService;

    public TaskAttachmentService(TaskAttachmentRepository repository,
                                 TaskRepository taskRepository,
                                 FileClient fileClient,
                                 UserClientHelper userClientHelper,
                                 OutboxWriter outboxWriter,
                                 TaskParticipantService participantService) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.fileClient = fileClient;
        this.userClientHelper = userClientHelper;
        this.outboxWriter = outboxWriter;
        this.participantService = participantService;
    }

    /** Returns all attachments for the given task, enriched with uploader display names. */
    public List<TaskAttachmentResponse> findByTaskId(UUID taskId) {
        getTaskOrThrow(taskId);
        return toResponseList(repository.findByTaskIdOrderByUploadedAtAsc(taskId));
    }

    /**
     * Registers an already-uploaded file as an attachment on the task.
     * Resolves the caller's user ID from the authentication context and publishes an ATTACHMENT_ADDED event.
     */
    @Transactional
    public TaskAttachmentResponse create(UUID taskId, TaskAttachmentRequest request, Authentication auth) {
        Task task = getTaskOrThrow(taskId);
        UUID uploadedByUserId = userClientHelper.resolveUserId(auth);
        TaskAttachment saved = repository.save(TaskAttachment.builder()
                .taskId(taskId)
                .fileId(request.getFileId())
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .uploadedByUserId(uploadedByUserId)
                .uploadedAt(Instant.now())
                .build());
        outboxWriter.write(TaskChangedEvent.attachmentAdded(
                taskId, task.getProjectId(), task.getTitle(),
                saved.getId(), saved.getFileName(), uploadedByUserId));
        //TODO: tight coupling with participantService, replace with call from frontend
        // Auto-register the uploader as a CONTRIBUTOR if not already a participant
        participantService.addIfNotPresent(taskId, uploadedByUserId, TaskParticipantRole.CONTRIBUTOR);
        String uploaderName = userClientHelper.resolveUserName(uploadedByUserId);
        return toResponse(saved, uploaderName);
    }

    /**
     * Hard-deletes the attachment record and removes the file from file-service/MinIO.
     * The caller's Bearer token is forwarded to file-service so it can verify deletion rights.
     * Publishes an ATTACHMENT_DELETED outbox event.
     *
     * @param bearerToken the caller's {@code Authorization: Bearer <token>} value
     */
    @Transactional
    public void delete(UUID taskId, UUID attachmentId, String bearerToken) {
        Task task = getTaskOrThrow(taskId);
        TaskAttachment attachment = getOrThrow(attachmentId);
        // Delete from MinIO via file-service before removing the metadata record
        fileClient.deleteFile(attachment.getFileId(), bearerToken);
        repository.deleteById(attachmentId);
        outboxWriter.write(TaskChangedEvent.attachmentDeleted(
                taskId, task.getProjectId(), task.getTitle(),
                attachmentId, attachment.getFileName()));
    }

    private Task getTaskOrThrow(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    private TaskAttachment getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY_NAME, id));
    }

    /** Batch-loads uploader names to avoid N+1 queries. */
    private List<TaskAttachmentResponse> toResponseList(List<TaskAttachment> attachments) {
        if (attachments.isEmpty()) return List.of();
        Set<UUID> uploaderIds = attachments.stream()
                .map(TaskAttachment::getUploadedByUserId)
                .collect(Collectors.toSet());
        Map<UUID, String> nameById = userClientHelper.fetchUserNames(uploaderIds);
        return attachments.stream()
                .map(a -> toResponse(a, nameById.get(a.getUploadedByUserId())))
                .toList();
    }

    private TaskAttachmentResponse toResponse(TaskAttachment attachment, String uploaderName) {
        return new TaskAttachmentResponse(
                attachment.getId(),
                attachment.getFileId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getUploadedByUserId(),
                uploaderName,
                attachment.getUploadedAt());
    }
}
