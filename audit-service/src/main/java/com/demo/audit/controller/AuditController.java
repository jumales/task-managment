package com.demo.audit.controller;

import com.demo.audit.model.StatusAuditRecord;
import com.demo.audit.model.BookedWorkAuditRecord;
import com.demo.audit.model.CommentAuditRecord;
import com.demo.audit.model.PhaseAuditRecord;
import com.demo.audit.model.PlannedWorkAuditRecord;
import com.demo.audit.repository.AuditRepository;
import com.demo.audit.repository.BookedWorkAuditRepository;
import com.demo.audit.repository.CommentAuditRepository;
import com.demo.audit.repository.PhaseAuditRepository;
import com.demo.audit.repository.PlannedWorkAuditRepository;
import com.demo.common.dto.PageResponse;
import com.demo.common.web.ResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Audit", description = "Query change history of tasks")
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditRepository auditRepository;
    private final CommentAuditRepository commentAuditRepository;
    private final PhaseAuditRepository phaseAuditRepository;
    private final PlannedWorkAuditRepository plannedWorkAuditRepository;
    private final BookedWorkAuditRepository bookedWorkAuditRepository;

    public AuditController(AuditRepository auditRepository,
                           CommentAuditRepository commentAuditRepository,
                           PhaseAuditRepository phaseAuditRepository,
                           PlannedWorkAuditRepository plannedWorkAuditRepository,
                           BookedWorkAuditRepository bookedWorkAuditRepository) {
        this.auditRepository = auditRepository;
        this.commentAuditRepository = commentAuditRepository;
        this.phaseAuditRepository = phaseAuditRepository;
        this.plannedWorkAuditRepository = plannedWorkAuditRepository;
        this.bookedWorkAuditRepository = bookedWorkAuditRepository;
    }

    /** Returns a paginated page of status transitions for the given task, ordered chronologically. */
    @Operation(summary = "Get status change history for a task",
               description = "Returns a paginated page of status transitions for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}/statuses")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<StatusAuditRecord> getStatusHistory(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId,
            @PageableDefault(size = 20, sort = "changedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return toPageResponse(auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, pageable));
    }

    /** Returns a paginated page of comment additions for the given task, ordered chronologically. */
    @Operation(summary = "Get comment change history for a task",
               description = "Returns a paginated page of comment edits for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}/comments")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CommentAuditRecord> getCommentHistory(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId,
            @PageableDefault(size = 20, sort = "addedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return toPageResponse(commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, pageable));
    }

    /** Returns a paginated page of phase transitions for the given task, ordered chronologically. */
    @Operation(summary = "Get phase change history for a task",
               description = "Returns a paginated page of phase transitions for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}/phases")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<PhaseAuditRecord> getPhaseHistory(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId,
            @PageableDefault(size = 20, sort = "changedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return toPageResponse(phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, pageable));
    }

    /** Returns a paginated page of planned-work creates for the given task, ordered chronologically. */
    @Operation(summary = "Get planned-work history for a task",
               description = "Returns a paginated page of planned-work creates for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}/planned-work")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<PlannedWorkAuditRecord> getPlannedWorkHistory(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId,
            @PageableDefault(size = 20, sort = "changedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return toPageResponse(plannedWorkAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, pageable));
    }

    /** Returns a paginated page of booked-work changes (create/update/delete) for the given task, ordered chronologically. */
    @Operation(summary = "Get booked-work history for a task",
               description = "Returns a paginated page of booked-work creates, updates, and deletes for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}/booked-work")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<BookedWorkAuditRecord> getBookedWorkHistory(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId,
            @PageableDefault(size = 20, sort = "changedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return toPageResponse(bookedWorkAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, pageable));
    }

    /** Converts a {@link Page} to a {@link PageResponse}. */
    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
