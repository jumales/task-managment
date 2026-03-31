package com.demo.user.controller;

import com.demo.common.dto.PageResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "Users", description = "Create and manage user accounts")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /** Returns a paginated list of all users. */
    @Operation(summary = "List all users")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<UserDto> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(pageable);
    }

    /** Returns the profile of the currently authenticated user, resolved from the JWT subject. */
    @Operation(summary = "Get the current user's profile")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Current user found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User not found")
    })
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserDto getMe(Authentication authentication) {
        return service.findById(UUID.fromString(authentication.getName()));
    }

    /** Returns the active user with the given username; used by task-service to resolve the caller's user-service UUID from the JWT preferred_username claim. */
    @Operation(summary = "Find a user by username")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "User found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User not found")
    })
    @GetMapping("/by-username")
    @PreAuthorize("isAuthenticated()")
    public UserDto getByUsername(@RequestParam String username) {
        return service.findByUsername(username)
                .orElseThrow(() -> new com.demo.common.exception.ResourceNotFoundException("User with username", username));
    }

    /** Returns users whose IDs match the provided list; used for batch lookups by other services. */
    @Operation(summary = "Batch-fetch users by IDs")
    @GetMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    public List<UserDto> getByIds(@RequestParam("ids") List<UUID> ids) {
        return service.findByIds(ids);
    }

    /** Returns the user identified by {@code id}. */
    @Operation(summary = "Get a user by ID")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "User found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
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
     * Updates the user's preferred UI language.
     * The request body must contain {@code "language"} (ISO 639-1 code, e.g. "en" or "hr").
     * Only admins may change the language preference of any user.
     */
    @Operation(summary = "Set preferred UI language for a user")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Language updated"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "User not found")
    })
    @PatchMapping("/{id}/language")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto updateLanguage(
            @Parameter(description = "User UUID") @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return service.updateLanguage(id, body.get("language"));
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
