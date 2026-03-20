package com.demo.audit.repository;

import com.demo.audit.model.CommentAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentAuditRepository extends JpaRepository<CommentAuditRecord, UUID> {
    /** Returns all comment audit records for the given task, ordered by addition time ascending. */
    List<CommentAuditRecord> findByTaskIdOrderByAddedAtAsc(UUID taskId);
}
