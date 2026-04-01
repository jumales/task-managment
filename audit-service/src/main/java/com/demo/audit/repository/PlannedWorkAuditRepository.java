package com.demo.audit.repository;

import com.demo.audit.model.PlannedWorkAuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlannedWorkAuditRepository extends JpaRepository<PlannedWorkAuditRecord, UUID> {

    /** Returns a paginated page of planned-work audit records for the given task, ordered by change time ascending. */
    Page<PlannedWorkAuditRecord> findByTaskIdOrderByChangedAtAsc(UUID taskId, Pageable pageable);
}
