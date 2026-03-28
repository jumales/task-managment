package com.demo.audit.repository;

import com.demo.audit.model.CommentAuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentAuditRepository extends JpaRepository<CommentAuditRecord, UUID> {
    /** Returns a paginated page of comment audit records for the given task, ordered by addition time ascending. */
    Page<CommentAuditRecord> findByTaskIdOrderByAddedAtAsc(UUID taskId, Pageable pageable);
}
