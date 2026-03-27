package com.demo.search.controller;

import com.demo.search.document.TaskDocument;
import com.demo.search.document.UserDocument;
import com.demo.search.service.ReindexService;
import com.demo.search.service.TaskIndexService;
import com.demo.search.service.UserIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    private final ReindexService reindexService;

    public SearchController(TaskIndexService taskIndexService,
                            UserIndexService userIndexService,
                            ReindexService reindexService) {
        this.taskIndexService = taskIndexService;
        this.userIndexService = userIndexService;
        this.reindexService = reindexService;
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

    /**
     * Re-indexes all users and tasks from the source services into Elasticsearch.
     * Use this after first startup or when the index is out of sync with the database.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Re-index all data", description = "Fetches all users and tasks from source services and rebuilds the Elasticsearch index")
    public Map<String, Integer> reindex() {
        int users = reindexService.reindexUsers();
        int tasks = reindexService.reindexTasks();
        return Map.of("users", users, "tasks", tasks);
    }
}
