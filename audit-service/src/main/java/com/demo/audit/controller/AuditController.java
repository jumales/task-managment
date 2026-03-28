package com.demo.audit.controller;

import com.demo.audit.model.AuditRecord;
import com.demo.audit.model.CommentAuditRecord;
import com.demo.audit.model.PhaseAuditRecord;
import com.demo.audit.model.WorkLogAuditRecord;
import com.demo.audit.repository.AuditRepository;
import com.demo.audit.repository.CommentAuditRepository;
import com.demo.audit.repository.PhaseAuditRepository;
import com.demo.audit.repository.WorkLogAuditRepository;
import com.demo.common.web.ResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Audit", description = "Query change history of tasks")
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditRepository auditRepository;
    private final CommentAuditRepository commentAuditRepository;
    private final PhaseAuditRepository phaseAuditRepository;
    private final WorkLogAuditRepository workLogAuditRepository;

    public AuditController(AuditRepository auditRepository,
                           CommentAuditRepository commentAuditRepository,
                           PhaseAuditRepository phaseAuditRepository,
                           WorkLogAuditRepository workLogAuditRepository) {
        this.auditRepository = auditRepository;
        this.commentAuditRepository = commentAuditRepository;
        this.phaseAuditRepository = phaseAuditRepository;
        this.workLogAuditRepository = workLogAuditRepository;
    }

    /** Returns all recorded status transitions for the given task, ordered chronologically. */
    @Operation(summary = "Get status change history for a task",
               description = "Returns all status transitions for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}/statuses")
    public List<AuditRecord> getStatusHistory(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId) {
        return auditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
    }

    /** Returns all recorded comment additions for the given task, ordered chronologically. */
    @Operation(summary = "Get comment change history for a task",
               description = "Returns all comment edits for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}/comments")
    public List<CommentAuditRecord> getCommentHistory(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId) {
        return commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId);
    }

    /** Returns all recorded phase transitions for the given task, ordered chronologically. */
    @Operation(summary = "Get phase change history for a task",
               description = "Returns all phase transitions for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}/phases")
    public List<PhaseAuditRecord> getPhaseHistory(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId) {
        return phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
    }

    /** Returns all recorded work log changes (create/update/delete) for the given task, ordered chronologically. */
    @Operation(summary = "Get work log change history for a task",
               description = "Returns all work log creates, updates, and deletes for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}/work-logs")
    public List<WorkLogAuditRecord> getWorkLogHistory(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId) {
        return workLogAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
    }
}
