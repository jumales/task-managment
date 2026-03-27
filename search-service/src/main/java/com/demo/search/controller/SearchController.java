package com.demo.search.controller;

import com.demo.search.document.TaskDocument;
import com.demo.search.document.UserDocument;
import com.demo.search.service.TaskIndexService;
import com.demo.search.service.UserIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for full-text search across tasks and users using Elasticsearch.
 * Results are ranked by Elasticsearch relevance score.
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Full-text search across tasks and users")
public class SearchController {

    private final TaskIndexService taskIndexService;
    private final UserIndexService userIndexService;

    public SearchController(TaskIndexService taskIndexService, UserIndexService userIndexService) {
        this.taskIndexService = taskIndexService;
        this.userIndexService = userIndexService;
    }

    /**
     * Searches tasks by free-text query across title and description.
     *
     * @param q the search query string
     * @return list of matching task documents ordered by relevance
     */
    @GetMapping("/tasks")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search tasks", description = "Full-text search across task title and description")
    public List<TaskDocument> searchTasks(
            @Parameter(description = "Search query") @RequestParam String q) {
        return taskIndexService.search(q);
    }

    /**
     * Searches users by free-text query across name, email, and username.
     *
     * @param q the search query string
     * @return list of matching user documents ordered by relevance
     */
    @GetMapping("/users")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search users", description = "Full-text search across user name, email, and username")
    public List<UserDocument> searchUsers(
            @Parameter(description = "Search query") @RequestParam String q) {
        return userIndexService.search(q);
    }
}
