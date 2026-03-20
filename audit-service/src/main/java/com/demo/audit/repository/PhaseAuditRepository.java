package com.demo.audit.repository;

import com.demo.audit.model.PhaseAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhaseAuditRepository extends JpaRepository<PhaseAuditRecord, UUID> {
    /** Returns all phase-change audit records for the given task, ordered by change time ascending. */
    List<PhaseAuditRecord> findByTaskIdOrderByChangedAtAsc(UUID taskId);
}
