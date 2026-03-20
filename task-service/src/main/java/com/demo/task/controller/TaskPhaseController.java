package com.demo.task.controller;

import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.task.service.TaskPhaseService;
import com.demo.common.web.ResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Phases", description = "Manage task phases per project. A project may have one default phase that is auto-assigned to new tasks.")
@RestController
@RequestMapping("/api/v1/phases")
public class TaskPhaseController {

    private final TaskPhaseService service;

    public TaskPhaseController(TaskPhaseService service) {
        this.service = service;
    }

    /** Returns all phases belonging to the given project. */
    @Operation(summary = "List phases for a project")
    @ApiResponse(responseCode = ResponseCode.OK, description = "Phases returned")
    @GetMapping
    public List<TaskPhaseResponse> getByProject(
            @Parameter(description = "Project UUID", required = true) @RequestParam UUID projectId) {
        return service.findByProject(projectId);
    }

    /** Returns the phase identified by {@code id}. */
    @Operation(summary = "Get a phase by ID")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Phase found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Phase not found")
    })
    @GetMapping("/{id}")
    public TaskPhaseResponse getById(@Parameter(description = "Phase UUID") @PathVariable UUID id) {
        return service.findById(id);
    }

    /** Creates a new phase for the project specified in the request body. */
    @Operation(summary = "Create a phase",
               description = "Set isDefault=true to make this the default phase for the project. Any existing default is replaced.")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.CREATED, description = "Phase created"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Project not found")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskPhaseResponse create(@RequestBody TaskPhaseRequest request) {
        return service.create(request);
    }

    /** Updates the phase identified by {@code id} with values from the request body. */
    @Operation(summary = "Update a phase",
               description = "Set isDefault=true to make this the default phase for the project. Any existing default is replaced.")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Phase updated"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Phase not found")
    })
    @PutMapping("/{id}")
    public TaskPhaseResponse update(@Parameter(description = "Phase UUID") @PathVariable UUID id,
                                    @RequestBody TaskPhaseRequest request) {
        return service.update(id, request);
    }

    /** Soft-deletes the phase identified by {@code id}. */
    @Operation(summary = "Delete a phase")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "Phase deleted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Phase not found"),
            @ApiResponse(responseCode = ResponseCode.CONFLICT, description = "Phase still has active tasks")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "Phase UUID") @PathVariable UUID id) {
        service.delete(id);
    }
}
