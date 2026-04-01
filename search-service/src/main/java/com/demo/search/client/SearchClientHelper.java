package com.demo.search.client;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskSummaryResponse;
import com.demo.common.dto.UserDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resilient wrapper around {@link TaskServiceClient} and {@link UserServiceClient}
 * that applies a circuit breaker to each page fetch during reindex.
 * When the circuit opens the fallback re-throws so {@link com.demo.search.service.ReindexService}
 * can record the failure and stop hammering an unavailable downstream service.
 */
@Component
public class SearchClientHelper {

    private static final Logger log = LoggerFactory.getLogger(SearchClientHelper.class);

    private final TaskServiceClient taskClient;
    private final UserServiceClient userClient;

    public SearchClientHelper(TaskServiceClient taskClient, UserServiceClient userClient) {
        this.taskClient = taskClient;
        this.userClient = userClient;
    }

    /**
     * Fetches one page of tasks from task-service.
     * Opens the circuit after repeated failures so the reindex loop aborts quickly
     * instead of blocking on every page.
     */
    @CircuitBreaker(name = "searchTaskService", fallbackMethod = "fetchTaskPageFallback")
    public PageResponse<TaskSummaryResponse> fetchTaskPage(int page, int size) {
        return taskClient.getAll(page, size);
    }

    private PageResponse<TaskSummaryResponse> fetchTaskPageFallback(int page, int size, Throwable t) {
        log.warn("task-service unavailable — aborting task reindex at page {}: {}", page, t.getMessage());
        throw new RuntimeException("task-service unavailable during reindex", t);
    }

    /**
     * Fetches one page of users from user-service.
     * Opens the circuit after repeated failures so the reindex loop aborts quickly
     * instead of blocking on every page.
     */
    @CircuitBreaker(name = "searchUserService", fallbackMethod = "fetchUserPageFallback")
    public PageResponse<UserDto> fetchUserPage(int page, int size) {
        return userClient.getAll(page, size);
    }

    private PageResponse<UserDto> fetchUserPageFallback(int page, int size, Throwable t) {
        log.warn("user-service unavailable — aborting user reindex at page {}: {}", page, t.getMessage());
        throw new RuntimeException("user-service unavailable during reindex", t);
    }
}
