package com.demo.audit.repository;

import com.demo.audit.model.PhaseAuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PhaseAuditRepository extends JpaRepository<PhaseAuditRecord, UUID> {
    /** Returns a paginated page of phase-change audit records for the given task, ordered by change time ascending. */
    Page<PhaseAuditRecord> findByTaskIdOrderByChangedAtAsc(UUID taskId, Pageable pageable);
}
