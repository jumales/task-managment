package com.demo.task.controller;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.PlannedDatesRequest;
import com.demo.common.dto.TaskCommentRequest;
import com.demo.common.dto.TaskCommentResponse;
import com.demo.common.dto.TaskFullResponse;
import com.demo.common.dto.TaskPhaseUpdateRequest;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskSummaryResponse;
import com.demo.task.client.UserClientHelper;
import com.demo.task.service.TaskService;
import com.demo.common.web.ResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Tasks", description = "Create and manage tasks. Tasks can be filtered by assigned user or status.")
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService service;
    private final UserClientHelper userClientHelper;

    public TaskController(TaskService service, UserClientHelper userClientHelper) {
        this.service = service;
        this.userClientHelper = userClientHelper;
    }

    /**
     * Returns a paginated list of tasks, with optional filtering by user, project, or status.
     * Supports {@code ?page=0&size=20&sort=title,asc} query parameters.
     *
     * @param userId    filter by assigned user UUID (optional)
     * @param projectId filter by project UUID (optional)
     * @param status    filter by task status string, case-insensitive (optional)
     * @param pageable  pagination parameters (page, size, sort)
     */
    @Operation(summary = "List all tasks (paginated)",
               description = "Returns a page of tasks. Optionally filter by `userId`, `projectId`, or `status`. "
                           + "Use `page`, `size`, and `sort` query parameters to control pagination.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<TaskSummaryResponse> getAll(
            @Parameter(description = "Filter by assigned user UUID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Filter by project UUID") @RequestParam(required = false) UUID projectId,
            @Parameter(description = "Filter by status (case-insensitive): TODO, IN_PROGRESS, DONE") @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        if (userId != null) return service.findByUser(userId, pageable);
        if (projectId != null) return service.findByProject(projectId, pageable);
        if (status != null) return service.findByStatus(status, pageable);
        return service.findAll(pageable);
    }

    /** Returns the task identified by {@code id}. */
    @Operation(summary = "Get a task by ID")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Task found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Task not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public TaskResponse getById(@Parameter(description = "Task UUID") @PathVariable UUID id) {
        return service.findById(id);
    }

    /**
     * Returns the full task view, including timeline, planned work, booked work, and assigned-user details.
     * Timeline, planned work, and booked work are fetched concurrently.
     * Comments are excluded — use {@code GET /api/v1/tasks/{id}/comments} separately.
     */
    @Operation(summary = "Get full task view",
               description = "Returns the task with all related data: participants, project, phase, "
                           + "assigned user profile, timeline, planned work, and booked work. "
                           + "Comments are fetched separately via the /comments endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Full task view returned"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Task not found")
    })
    @GetMapping("/{id}/full")
    @PreAuthorize("isAuthenticated()")
    public TaskFullResponse getFullById(@Parameter(description = "Task UUID") @PathVariable UUID id) {
        return service.findFullById(id);
    }

    /** Creates a new task and returns the persisted representation. */
    @Operation(summary = "Create a new task")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.CREATED, description = "Task created"),
            @ApiResponse(responseCode = ResponseCode.INTERNAL_SERVER_ERROR, description = "Assigned user not found in User Service")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public TaskResponse create(@RequestBody TaskRequest request, Authentication authentication) {
        UUID creatorId = resolveUserId(authentication);
        return service.create(request, creatorId);
    }

    /** Resolves the caller's user-service UUID from the Spring Security authentication object. */
    private UUID resolveUserId(Authentication authentication) {
        return userClientHelper.resolveUserId(authentication);
    }

    /** Updates the task identified by {@code id} with the values from the request body. */
    @Operation(summary = "Update an existing task")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Task updated"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Task not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public TaskResponse update(@Parameter(description = "Task UUID") @PathVariable UUID id,
                               @RequestBody TaskRequest request) {
        return service.update(id, request);
    }

    /**
     * Changes the phase of the task identified by {@code id}.
     * Enforces the one-way gate: a task that has left PLANNING may never return to it.
     */
    @Operation(summary = "Change the phase of a task",
               description = "Updates only the phase field. "
                           + "Fails with 400 if the phase does not belong to the task's project "
                           + "or if the request attempts to return a task to the PLANNING phase.")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Phase updated"),
            @ApiResponse(responseCode = ResponseCode.BAD_REQUEST, description = "Invalid phase transition"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Task or phase not found")
    })
    @PatchMapping("/{id}/phase")
    @PreAuthorize("isAuthenticated()")
    public TaskResponse updatePhase(@Parameter(description = "Task UUID") @PathVariable UUID id,
                                    @RequestBody TaskPhaseUpdateRequest request) {
        return service.updatePhase(id, request.getPhaseId());
    }

    /** Returns all comments for the task, ordered by creation time ascending. */
    @Operation(summary = "Get comments for a task")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Comments returned"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Task not found")
    })
    @GetMapping("/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    public List<TaskCommentResponse> getComments(@Parameter(description = "Task UUID") @PathVariable UUID id) {
        return service.getComments(id);
    }

    /** Appends a comment to the task and returns the created comment. */
    @Operation(summary = "Add a comment to a task")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.CREATED, description = "Comment added"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Task not found")
    })
    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public TaskCommentResponse addComment(@Parameter(description = "Task UUID") @PathVariable UUID id,
                                          @RequestBody TaskCommentRequest request,
                                          Authentication authentication) {
        UUID authorId = resolveUserId(authentication);
        return service.addComment(id, request, authorId);
    }

    /**
     * Updates the planned start and end dates for the task.
     * Only allowed while the task is in the PLANNING phase; returns 400 otherwise.
     */
    @Operation(summary = "Update planned dates",
               description = "Atomically sets PLANNED_START and PLANNED_END for a task. "
                           + "Fails with 400 if the task is not in the PLANNING phase or if plannedStart is not before plannedEnd.")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Planned dates updated"),
            @ApiResponse(responseCode = ResponseCode.BAD_REQUEST, description = "Task not in PLANNING phase or invalid date ordering"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Task not found")
    })
    @PutMapping("/{id}/planned-dates")
    @PreAuthorize("isAuthenticated()")
    public TaskFullResponse updatePlannedDates(@Parameter(description = "Task UUID") @PathVariable UUID id,
                                               @RequestBody PlannedDatesRequest request,
                                               Authentication authentication) {
        UUID updatingUserId = resolveUserId(authentication);
        return service.updatePlannedDates(id, updatingUserId, request);
    }

    /** Soft-deletes the task identified by {@code id}. */
    @Operation(summary = "Delete a task")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "Task deleted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Task not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void delete(@Parameter(description = "Task UUID") @PathVariable UUID id) {
        service.delete(id);
    }
}
