package com.demo.search.service;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.UserDto;
import com.demo.search.client.TaskServiceClient;
import com.demo.search.client.UserServiceClient;
import com.demo.search.document.TaskDocument;
import com.demo.search.document.UserDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches all users and tasks from their respective services and re-indexes them in Elasticsearch.
 * Use this to populate the index on first run or to recover from index corruption.
 */
@Service
public class ReindexService {

    private static final Logger log = LoggerFactory.getLogger(ReindexService.class);
    private static final int PAGE_SIZE = 200;

    private final UserServiceClient userClient;
    private final TaskServiceClient taskClient;
    private final UserIndexService userIndexService;
    private final TaskIndexService taskIndexService;
    private final ElasticsearchOperations esOps;

    public ReindexService(UserServiceClient userClient,
                          TaskServiceClient taskClient,
                          UserIndexService userIndexService,
                          TaskIndexService taskIndexService,
                          ElasticsearchOperations esOps) {
        this.userClient = userClient;
        this.taskClient = taskClient;
        this.userIndexService = userIndexService;
        this.taskIndexService = taskIndexService;
        this.esOps = esOps;
    }

    /** Drops and recreates the user index, then fetches all users page by page and re-indexes them. */
    public int reindexUsers() {
        var indexOps = esOps.indexOps(UserDocument.class);
        indexOps.delete();
        indexOps.createWithMapping();
        List<UserDto> all = fetchAllUsers();
        userIndexService.indexAll(all);
        log.info("Re-indexed {} users", all.size());
        return all.size();
    }

    /** Drops and recreates the task index, then fetches all tasks page by page and re-indexes them. */
    public int reindexTasks() {
        var indexOps = esOps.indexOps(TaskDocument.class);
        indexOps.delete();
        indexOps.createWithMapping();
        List<TaskResponse> all = fetchAllTasks();
        taskIndexService.indexAll(all);
        log.info("Re-indexed {} tasks", all.size());
        return all.size();
    }

    private List<UserDto> fetchAllUsers() {
        List<UserDto> all = new ArrayList<>();
        int page = 0;
        PageResponse<UserDto> response;
        do {
            response = userClient.getAll(page++, PAGE_SIZE);
            all.addAll(response.getContent());
        } while (!response.isLast());
        return all;
    }

    private List<TaskResponse> fetchAllTasks() {
        List<TaskResponse> all = new ArrayList<>();
        int page = 0;
        PageResponse<TaskResponse> response;
        do {
            response = taskClient.getAll(page++, PAGE_SIZE);
            all.addAll(response.getContent());
        } while (!response.isLast());
        return all;
    }
}
