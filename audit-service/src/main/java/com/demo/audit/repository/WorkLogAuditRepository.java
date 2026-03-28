package com.demo.audit.repository;

import com.demo.audit.model.WorkLogAuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkLogAuditRepository extends JpaRepository<WorkLogAuditRecord, UUID> {

    /** Returns a paginated page of work log audit records for the given task, ordered by change time ascending. */
    Page<WorkLogAuditRecord> findByTaskIdOrderByChangedAtAsc(UUID taskId, Pageable pageable);
}
