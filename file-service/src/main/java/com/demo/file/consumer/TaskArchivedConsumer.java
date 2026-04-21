package com.demo.file.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskEvent;
import com.demo.common.event.TaskEventType;
import com.demo.file.repository.FileMetadataRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Consumes {@code TASK_ARCHIVED} events and soft-deletes the associated file metadata records
 * so that the {@link com.demo.file.scheduler.FileCleanupScheduler} can later purge them from MinIO.
 *
 * <p>Idempotency: no dedup table is needed — {@code softDeleteById} is a no-op once
 * {@code deleted_at} is set, so duplicate deliveries converge to the same row state.
 */
@Component
public class TaskArchivedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskArchivedConsumer.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final ObjectMapper objectMapper;

    public TaskArchivedConsumer(FileMetadataRepository fileMetadataRepository, ObjectMapper objectMapper) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives a raw JSON task event; soft-deletes file_metadata rows for every file ID
     * listed in the {@code TASK_ARCHIVED} payload. Ignores all other event types.
     *
     * @throws JsonProcessingException if the message cannot be deserialized — propagates to DLT
     */
    @KafkaListener(topics = KafkaTopics.TASK_EVENTS, groupId = "file-archive-group")
    public void consume(String message) throws JsonProcessingException {
        TaskEvent event = objectMapper.readValue(message, TaskEvent.class);
        if (event.getEventType() != TaskEventType.ARCHIVED) {
            return;
        }

        List<UUID> fileIds = event.getArchivedFileIds();
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        log.info("Soft-deleting {} file(s) for archived task={}", fileIds.size(), event.getTaskId());
        for (UUID fileId : fileIds) {
            fileMetadataRepository.softDeleteById(fileId);
        }
    }
}
