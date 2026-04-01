package com.demo.search.service;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskSummaryResponse;
import com.demo.common.dto.UserDto;
import com.demo.search.client.SearchClientHelper;
import com.demo.search.document.TaskDocument;
import com.demo.search.document.UserDocument;
import com.demo.search.dto.ReindexResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches all users and tasks from their respective services and re-indexes them in Elasticsearch.
 * Use this to populate the index on first run or to recover from index corruption.
 * Partial failures (e.g. a downstream service going down mid-reindex) are recorded in the
 * returned {@link ReindexResult} rather than aborting the entire operation.
 */
@Service
public class ReindexService {

    private static final Logger log = LoggerFactory.getLogger(ReindexService.class);
    private static final int PAGE_SIZE = 200;

    private final SearchClientHelper clientHelper;
    private final UserIndexService userIndexService;
    private final TaskIndexService taskIndexService;
    private final ElasticsearchOperations esOps;

    public ReindexService(SearchClientHelper clientHelper,
                          UserIndexService userIndexService,
                          TaskIndexService taskIndexService,
                          ElasticsearchOperations esOps) {
        this.clientHelper = clientHelper;
        this.userIndexService = userIndexService;
        this.taskIndexService = taskIndexService;
        this.esOps = esOps;
    }

    /**
     * Drops and recreates both indexes, then fetches all users and tasks page by page.
     * Task reindex runs independently — a user-service failure does not block it.
     * Returns a {@link ReindexResult} with success counts and the number of pages that failed
     * due to downstream unavailability.
     */
    public ReindexResult reindex() {
        PageFetch<UserDto> userFetch = reindexUsers();
        PageFetch<TaskSummaryResponse> taskFetch = reindexTasks();
        return new ReindexResult(
                userFetch.items().size(), taskFetch.items().size(),
                userFetch.failedPages(), taskFetch.failedPages());
    }

    private PageFetch<UserDto> reindexUsers() {
        esOps.indexOps(UserDocument.class).delete();
        esOps.indexOps(UserDocument.class).createWithMapping();
        PageFetch<UserDto> result = fetchAllUsers();
        userIndexService.indexAll(result.items());
        if (result.failedPages() > 0) {
            log.warn("User reindex completed with {} failed pages — index may be incomplete", result.failedPages());
        }
        log.info("Re-indexed {} users ({} pages failed)", result.items().size(), result.failedPages());
        return result;
    }

    private PageFetch<TaskSummaryResponse> reindexTasks() {
        esOps.indexOps(TaskDocument.class).delete();
        esOps.indexOps(TaskDocument.class).createWithMapping();
        PageFetch<TaskSummaryResponse> result = fetchAllTasks();
        taskIndexService.indexAll(result.items());
        if (result.failedPages() > 0) {
            log.warn("Task reindex completed with {} failed pages — index may be incomplete", result.failedPages());
        }
        log.info("Re-indexed {} tasks ({} pages failed)", result.items().size(), result.failedPages());
        return result;
    }

    /**
     * Fetches all user pages via the circuit-breaker-protected helper.
     * On failure, stops paging and records the number of failed pages so the caller
     * can report a partial result rather than failing entirely.
     */
    private PageFetch<UserDto> fetchAllUsers() {
        List<UserDto> all = new ArrayList<>();
        int page = 0;
        int failedPages = 0;
        try {
            PageResponse<UserDto> response;
            do {
                response = clientHelper.fetchUserPage(page++, PAGE_SIZE);
                all.addAll(response.getContent());
            } while (!response.isLast());
        } catch (RuntimeException e) {
            failedPages++;
            log.warn("Stopped fetching users after page {} due to error: {}", page, e.getMessage());
        }
        return new PageFetch<>(all, failedPages);
    }

    /**
     * Fetches all task pages via the circuit-breaker-protected helper.
     * On failure, stops paging and records the number of failed pages so the caller
     * can report a partial result rather than failing entirely.
     */
    private PageFetch<TaskSummaryResponse> fetchAllTasks() {
        List<TaskSummaryResponse> all = new ArrayList<>();
        int page = 0;
        int failedPages = 0;
        try {
            PageResponse<TaskSummaryResponse> response;
            do {
                response = clientHelper.fetchTaskPage(page++, PAGE_SIZE);
                all.addAll(response.getContent());
            } while (!response.isLast());
        } catch (RuntimeException e) {
            failedPages++;
            log.warn("Stopped fetching tasks after page {} due to error: {}", page, e.getMessage());
        }
        return new PageFetch<>(all, failedPages);
    }

    /** Bundles fetched items with the count of pages that could not be retrieved. */
    private record PageFetch<T>(List<T> items, int failedPages) {}
}
