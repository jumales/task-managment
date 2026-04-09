package com.demo.reporting.controller;

import com.demo.reporting.dto.MyTaskResponse;
import com.demo.reporting.service.MyTasksService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for the reporting-service. Currently exposes the "My Tasks" view;
 * hours reports are added in a follow-up PR.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final MyTasksService myTasksService;

    public ReportingController(MyTasksService myTasksService) {
        this.myTasksService = myTasksService;
    }

    /**
     * Returns all tasks assigned to the current user. When {@code days} is provided the
     * result is limited to tasks updated within the last {@code days} days (e.g. 5 or 30).
     */
    @Operation(summary = "My tasks",
               description = "Tasks assigned to the authenticated user. Optional `days` parameter limits the result to the last N days.")
    @GetMapping("/my-tasks")
    @PreAuthorize("isAuthenticated()")
    public List<MyTaskResponse> getMyTasks(
            @Parameter(description = "Return only tasks updated within the last N days (e.g. 5 or 30).")
            @RequestParam(required = false) Integer days,
            Authentication authentication) {
        return myTasksService.findMyTasks(resolveUserId(authentication), days);
    }

    /**
     * Resolves the current user id from the authentication principal.
     * Prod: JWT with UUID {@code sub} claim. Tests: {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken}
     * whose principal is the UUID string.
     */
    private UUID resolveUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        return UUID.fromString(authentication.getName());
    }
}
