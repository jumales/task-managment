package com.demo.audit.repository;

import com.demo.audit.model.AuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditRecord, UUID> {
    /** Returns a paginated page of status-change audit records for the given task, ordered by change time ascending. */
    Page<AuditRecord> findByTaskIdOrderByChangedAtAsc(UUID taskId, Pageable pageable);
}
