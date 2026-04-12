package com.demo.task.controller;

import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.task.service.TaskProjectService;
import com.demo.common.web.ResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Projects", description = "Manage task projects. Every task belongs to exactly one project.")
@RestController
@RequestMapping("/api/v1/projects")
public class TaskProjectController {

    private final TaskProjectService service;

    public TaskProjectController(TaskProjectService service) {
        this.service = service;
    }

    /** Returns a page of projects. Supports {@code ?page}, {@code ?size}, and {@code ?sort} query params. */
    @Operation(summary = "List projects (paginated)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<TaskProjectResponse> getAll(@PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return service.findAll(pageable);
    }

    /** Returns the project identified by {@code id}. */
    @Operation(summary = "Get a project by ID")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Project found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Project not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public TaskProjectResponse getById(@Parameter(description = "Project UUID") @PathVariable UUID id) {
        return service.findById(id);
    }

    /** Creates a new project and returns the persisted representation. */
    @Operation(summary = "Create a new project")
    @ApiResponse(responseCode = ResponseCode.CREATED, description = "Project created")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TaskProjectResponse create(@RequestBody TaskProjectRequest request) {
        return service.create(request);
    }

    /** Updates the project identified by {@code id} with values from the request body. */
    @Operation(summary = "Update a project")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Project updated"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Project not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskProjectResponse update(@Parameter(description = "Project UUID") @PathVariable UUID id,
                                      @RequestBody TaskProjectRequest request) {
        return service.update(id, request);
    }

    /** Soft-deletes the project identified by {@code id}. */
    @Operation(summary = "Delete a project")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "Project deleted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Project not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@Parameter(description = "Project UUID") @PathVariable UUID id) {
        service.delete(id);
    }
}
