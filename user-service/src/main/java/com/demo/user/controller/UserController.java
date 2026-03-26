package com.demo.user.controller;

import com.demo.common.dto.UserDto;
import com.demo.common.dto.UserRequest;
import com.demo.user.service.UserService;
import com.demo.common.web.ResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.List;
import java.util.UUID;

@Tag(name = "Users", description = "Create and manage user accounts")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /** Returns all users. */
    @Operation(summary = "List all users")
    @GetMapping
    public List<UserDto> getAll() {
        return service.findAll();
    }

    /** Returns the user identified by {@code id}. */
    @Operation(summary = "Get a user by ID")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "User found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User not found")
    })
    @GetMapping("/{id}")
    public UserDto getById(@Parameter(description = "User UUID") @PathVariable UUID id) {
        return service.findById(id);
    }

    /** Creates a new user and returns the persisted representation. */
    @Operation(summary = "Create a new user")
    @ApiResponse(responseCode = ResponseCode.CREATED, description = "User created")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(@RequestBody @Valid UserRequest request) {
        return service.create(request);
    }

    /** Updates the user identified by {@code id} with values from the request body. */
    @Operation(summary = "Update an existing user")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "User updated"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto update(@Parameter(description = "User UUID") @PathVariable UUID id,
                          @RequestBody @Valid UserRequest request) {
        return service.update(id, request);
    }

    /** Soft-deletes the user identified by {@code id}. */
    @Operation(summary = "Delete a user")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "User deleted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@Parameter(description = "User UUID") @PathVariable UUID id) {
        service.delete(id);
    }

    /**
     * Sets or removes the user's profile picture.
     * The request body must contain {@code "fileId"} (UUID from file-service) or {@code null} to clear.
     */
    @Operation(summary = "Set or clear a user's profile picture")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Avatar updated"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User not found")
    })
    @PatchMapping("/{id}/avatar")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto updateAvatar(
            @Parameter(description = "User UUID") @PathVariable UUID id,
            @RequestBody Map<String, UUID> body) {
        return service.updateAvatar(id, body.get("fileId"));
    }

    /** Assigns the specified role to the user and returns the updated user. */
    @Operation(summary = "Assign a role to a user")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Role assigned"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User or role not found"),
            @ApiResponse(responseCode = ResponseCode.CONFLICT, description = "User already has this role")
    })
    @PostMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto assignRole(@Parameter(description = "User UUID") @PathVariable UUID userId,
                              @Parameter(description = "Role UUID") @PathVariable UUID roleId,
                              @Parameter(description = "Who is assigning the role") @RequestParam(defaultValue = "system") String assignedBy) {
        return service.assignRole(userId, roleId, assignedBy);
    }

    /** Revokes the specified role from the user. */
    @Operation(summary = "Revoke a role from a user")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "Role revoked"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User or role not found")
    })
    @DeleteMapping("/{userId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void revokeRole(@Parameter(description = "User UUID") @PathVariable UUID userId,
                           @Parameter(description = "Role UUID") @PathVariable UUID roleId) {
        service.revokeRole(userId, roleId);
    }
}
