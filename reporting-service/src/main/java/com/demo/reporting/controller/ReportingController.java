package com.demo.reporting.controller;

import com.demo.reporting.dto.DetailedHoursResponse;
import com.demo.reporting.dto.MyTaskResponse;
import com.demo.reporting.dto.ProjectHoursResponse;
import com.demo.reporting.dto.TaskHoursResponse;
import com.demo.reporting.service.HoursReportService;
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
 * REST endpoints for the reporting-service: "My Tasks" view plus planned-vs-booked hours
 * reports at task, project, and per user × work-type level.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final MyTasksService myTasksService;
    private final HoursReportService hoursReportService;

    public ReportingController(MyTasksService myTasksService, HoursReportService hoursReportService) {
        this.myTasksService = myTasksService;
        this.hoursReportService = hoursReportService;
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

    /** Planned vs booked hours per task, optionally filtered by {@code projectId}. */
    @Operation(summary = "Hours by task", description = "Planned vs booked hours per task, optionally scoped to a project.")
    @GetMapping("/hours/by-task")
    @PreAuthorize("isAuthenticated()")
    public List<TaskHoursResponse> getHoursByTask(
            @Parameter(description = "Restrict the result to tasks belonging to this project.")
            @RequestParam(required = false) UUID projectId) {
        return hoursReportService.byTask(projectId);
    }

    /** Planned vs booked hours per project (summed across all project tasks). */
    @Operation(summary = "Hours by project", description = "Planned vs booked hours totalled per project.")
    @GetMapping("/hours/by-project")
    @PreAuthorize("isAuthenticated()")
    public List<ProjectHoursResponse> getHoursByProject() {
        return hoursReportService.byProject();
    }

    /** Planned vs booked hours for a single task broken down by user and work type. */
    @Operation(summary = "Hours detailed", description = "Planned vs booked hours for a task broken down by user and work type.")
    @GetMapping("/hours/detailed")
    @PreAuthorize("isAuthenticated()")
    public List<DetailedHoursResponse> getHoursDetailed(
            @Parameter(description = "Task to break down.", required = true)
            @RequestParam UUID taskId) {
        return hoursReportService.detailed(taskId);
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
