package com.demo.search.client;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Feign client for fetching all tasks from task-service during re-indexing. */
@FeignClient(name = "task-service")
public interface TaskServiceClient {

    @GetMapping("/api/v1/tasks")
    PageResponse<TaskSummaryResponse> getAll(@RequestParam int page, @RequestParam int size);
}
