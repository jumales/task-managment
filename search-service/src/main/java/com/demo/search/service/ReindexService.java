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
        PageSummary users = reindexUsers();
        PageSummary tasks = reindexTasks();
        return new ReindexResult(users.indexed(), tasks.indexed(), users.failedPages(), tasks.failedPages());
    }

    private PageSummary reindexUsers() {
        esOps.indexOps(UserDocument.class).delete();
        esOps.indexOps(UserDocument.class).createWithMapping();
        PageSummary result = fetchAndIndexAllUsers();
        if (result.failedPages() > 0) {
            log.warn("User reindex completed with {} failed pages — index may be incomplete", result.failedPages());
        }
        log.info("Re-indexed {} users ({} pages failed)", result.indexed(), result.failedPages());
        return result;
    }

    private PageSummary reindexTasks() {
        esOps.indexOps(TaskDocument.class).delete();
        esOps.indexOps(TaskDocument.class).createWithMapping();
        PageSummary result = fetchAndIndexAllTasks();
        if (result.failedPages() > 0) {
            log.warn("Task reindex completed with {} failed pages — index may be incomplete", result.failedPages());
        }
        log.info("Re-indexed {} tasks ({} pages failed)", result.indexed(), result.failedPages());
        return result;
    }

    /**
     * Fetches user pages one at a time and indexes each immediately, so no full-dataset list
     * is held in memory. On failure, stops paging and records the failed page count.
     */
    private PageSummary fetchAndIndexAllUsers() {
        int indexed = 0;
        int page = 0;
        int failedPages = 0;
        try {
            PageResponse<UserDto> response;
            do {
                response = clientHelper.fetchUserPage(page++, PAGE_SIZE);
                userIndexService.indexAll(response.getContent());
                indexed += response.getContent().size();
            } while (!response.isLast());
        } catch (RuntimeException e) {
            failedPages++;
            log.warn("Stopped fetching users after page {} due to error: {}", page, e.getMessage());
        }
        return new PageSummary(indexed, failedPages);
    }

    /**
     * Fetches task pages one at a time and indexes each immediately, so no full-dataset list
     * is held in memory. On failure, stops paging and records the failed page count.
     */
    private PageSummary fetchAndIndexAllTasks() {
        int indexed = 0;
        int page = 0;
        int failedPages = 0;
        try {
            PageResponse<TaskSummaryResponse> response;
            do {
                response = clientHelper.fetchTaskPage(page++, PAGE_SIZE);
                taskIndexService.indexAll(response.getContent());
                indexed += response.getContent().size();
            } while (!response.isLast());
        } catch (RuntimeException e) {
            failedPages++;
            log.warn("Stopped fetching tasks after page {} due to error: {}", page, e.getMessage());
        }
        return new PageSummary(indexed, failedPages);
    }

    /** Tracks total items indexed and pages that failed during a reindex run. */
    private record PageSummary(int indexed, int failedPages) {}
}
