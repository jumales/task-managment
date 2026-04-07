package com.demo.task.repository;

import com.demo.task.model.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Repository for {@link TaskAttachment} — hard-delete entity, no soft-delete filter needed. */
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, UUID> {

    /** Returns all attachments for the given task ordered by upload time ascending. */
    List<TaskAttachment> findByTaskIdOrderByUploadedAtAsc(UUID taskId);
}
