package com.demo.task.controller;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskCommentRequest;
import com.demo.common.dto.TaskCommentResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Tasks", description = "Create and manage tasks. Tasks can be filtered by assigned user or status.")
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /**
     * Returns a paginated list of tasks, with optional filtering by user, project, or status.
     * Supports {@code ?page=0&size=20&sort=title,asc} query parameters.
     *
     * @param userId    filter by assigned user UUID (optional)
     * @param projectId filter by project UUID (optional)
     * @param status    filter by task status string, case-insensitive (optional)
     */
    @Operation(summary = "List all tasks (paginated)",
               description = "Returns a page of tasks. Optionally filter by `userId`, `projectId`, or `status`. "
                           + "Use `page`, `size`, and `sort` query parameters to control pagination.")
    @GetMapping
    public PageResponse<TaskResponse> getAll(
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
    public TaskResponse getById(@Parameter(description = "Task UUID") @PathVariable UUID id) {
        return service.findById(id);
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
    public TaskResponse create(@RequestBody TaskRequest request) {
        return service.create(request);
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

    /** Returns all comments for the task, ordered by creation time ascending. */
    @Operation(summary = "Get comments for a task")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Comments returned"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Task not found")
    })
    @GetMapping("/{id}/comments")
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
                                          @RequestBody TaskCommentRequest request) {
        return service.addComment(id, request);
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
