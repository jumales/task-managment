package com.demo.user.controller;

import com.demo.common.dto.RoleDto;
import com.demo.common.dto.RoleRequest;
import com.demo.user.service.RoleService;
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

@Tag(name = "Roles", description = "Create and manage roles. A role is a named collection of rights.")
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    /** Returns all roles. */
    @Operation(summary = "List all roles")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RoleDto> getAll() {
        return service.findAll();
    }

    /** Returns the role identified by {@code id}. */
    @Operation(summary = "Get a role by ID")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Role found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Role not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public RoleDto getById(@Parameter(description = "Role UUID") @PathVariable UUID id) {
        return service.findById(id);
    }

    /** Creates a new role and returns the persisted representation. */
    @Operation(summary = "Create a new role")
    @ApiResponse(responseCode = ResponseCode.CREATED, description = "Role created")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RoleDto create(@RequestBody RoleRequest request) {
        return service.create(request);
    }

    /** Soft-deletes the role identified by {@code id}. */
    @Operation(summary = "Delete a role")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "Role deleted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Role not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@Parameter(description = "Role UUID") @PathVariable UUID id) {
        service.delete(id);
    }

    /** Grants the specified right to the role and returns the updated role. */
    @Operation(summary = "Grant a right to a role")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Right granted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Role or right not found"),
            @ApiResponse(responseCode = ResponseCode.CONFLICT, description = "Role already has this right")
    })
    @PostMapping("/{roleId}/rights/{rightId}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoleDto grantRight(@Parameter(description = "Role UUID") @PathVariable UUID roleId,
                              @Parameter(description = "Right UUID") @PathVariable UUID rightId,
                              @Parameter(description = "Who is granting the right") @RequestParam(defaultValue = "system") String grantedBy) {
        return service.grantRight(roleId, rightId, grantedBy);
    }

    /** Revokes the specified right from the role. */
    @Operation(summary = "Revoke a right from a role")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "Right revoked"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Role or right not found")
    })
    @DeleteMapping("/{roleId}/rights/{rightId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void revokeRight(@Parameter(description = "Role UUID") @PathVariable UUID roleId,
                            @Parameter(description = "Right UUID") @PathVariable UUID rightId) {
        service.revokeRight(roleId, rightId);
    }
}
