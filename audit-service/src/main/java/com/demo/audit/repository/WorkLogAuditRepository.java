package com.demo.audit.repository;

import com.demo.audit.model.WorkLogAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkLogAuditRepository extends JpaRepository<WorkLogAuditRecord, UUID> {

    /** Returns all work log audit records for the given task, ordered by change time ascending. */
    List<WorkLogAuditRecord> findByTaskIdOrderByChangedAtAsc(UUID taskId);
}
