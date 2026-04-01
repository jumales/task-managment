package com.demo.search.dto;

/**
 * Summary of a reindex operation.
 * Reports how many records were successfully indexed and how many pages failed,
 * so callers can detect a partial reindex and decide whether to retry.
 *
 * @param indexedUsers     number of user documents successfully written to the index
 * @param indexedTasks     number of task documents successfully written to the index
 * @param failedUserPages  number of user pages that could not be fetched (service unavailable)
 * @param failedTaskPages  number of task pages that could not be fetched (service unavailable)
 */
public record ReindexResult(int indexedUsers, int indexedTasks, int failedUserPages, int failedTaskPages) {}
