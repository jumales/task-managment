package com.demo.notification.controller;

import com.demo.common.dto.DeviceTokenRequest;
import com.demo.common.dto.DeviceTokenResponse;
import com.demo.notification.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Manages FCM/APNs device tokens for the authenticated user.
 * All operations are scoped to the caller's own tokens — users cannot see or modify others' tokens.
 */
@Tag(name = "Device Tokens", description = "Register and manage push notification device tokens")
@RestController
@RequestMapping("/api/v1/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService service;

    public DeviceTokenController(DeviceTokenService service) {
        this.service = service;
    }

    /**
     * Registers a new push token for the caller, or refreshes an existing soft-deleted one.
     * Returns 201 Created with the stored token details.
     */
    @Operation(summary = "Register or refresh a device token")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public DeviceTokenResponse register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeviceTokenRequest request) {
        return service.register(resolveUserId(jwt), request);
    }

    /**
     * Rotates a device token: soft-deletes the old token and inserts the new one.
     * Used when the OS issues a new push token for an existing install.
     */
    @Operation(summary = "Rotate a device token")
    @PutMapping("/{oldToken}")
    @PreAuthorize("isAuthenticated()")
    public DeviceTokenResponse rotate(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "The token being replaced") @PathVariable String oldToken,
            @Valid @RequestBody DeviceTokenRequest request) {
        return service.rotate(resolveUserId(jwt), oldToken, request);
    }

    /** Soft-deletes the specified token for the caller (e.g. on logout). */
    @Operation(summary = "Remove a device token")
    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String token) {
        service.delete(resolveUserId(jwt), token);
    }

    /** Returns all active tokens registered by the caller. */
    @Operation(summary = "List active tokens for the caller")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<DeviceTokenResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        return service.listForUser(resolveUserId(jwt));
    }

    /** Extracts the user UUID from the JWT {@code sub} claim. */
    private UUID resolveUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
