package com.demo.audit.repository;

import com.demo.audit.model.BookedWorkAuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookedWorkAuditRepository extends JpaRepository<BookedWorkAuditRecord, UUID> {

    /** Returns a paginated page of booked-work audit records for the given task, ordered by change time ascending. */
    Page<BookedWorkAuditRecord> findByTaskIdOrderByChangedAtAsc(UUID taskId, Pageable pageable);
}
