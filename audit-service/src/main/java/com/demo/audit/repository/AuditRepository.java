package com.demo.audit.repository;

import com.demo.audit.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditRecord, UUID> {
    /** Returns all status-change audit records for the given task, ordered by change time ascending. */
    List<AuditRecord> findByTaskIdOrderByChangedAtAsc(UUID taskId);
}
