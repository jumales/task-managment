package com.demo.user.controller;

import com.demo.common.dto.RightDto;
import com.demo.common.dto.RightRequest;
import com.demo.user.service.RightService;
import com.demo.common.web.ResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Rights", description = "Create and manage fine-grained rights that can be bundled into roles")
@RestController
@RequestMapping("/api/v1/rights")
public class RightController {

    private final RightService service;

    public RightController(RightService service) {
        this.service = service;
    }

    /** Returns all rights. */
    @Operation(summary = "List all rights")
    @GetMapping
    public List<RightDto> getAll() {
        return service.findAll();
    }

    /** Returns the right identified by {@code id}. */
    @Operation(summary = "Get a right by ID")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Right found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Right not found")
    })
    @GetMapping("/{id}")
    public RightDto getById(@Parameter(description = "Right UUID") @PathVariable UUID id) {
        return service.findById(id);
    }

    /** Creates a new right and returns the persisted representation. */
    @Operation(summary = "Create a new right")
    @ApiResponse(responseCode = ResponseCode.CREATED, description = "Right created")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RightDto create(@RequestBody RightRequest request) {
        return service.create(request);
    }

    /** Soft-deletes the right identified by {@code id}. */
    @Operation(summary = "Delete a right")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "Right deleted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Right not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@Parameter(description = "Right UUID") @PathVariable UUID id) {
        service.delete(id);
    }
}
