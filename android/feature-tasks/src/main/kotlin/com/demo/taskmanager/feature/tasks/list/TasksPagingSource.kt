package com.demo.taskmanager.feature.tasks.list

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.enums.TaskStatus as DtoTaskStatus
import com.demo.taskmanager.data.mapper.toDomain
import com.demo.taskmanager.data.repo.TaskRepository
import com.demo.taskmanager.domain.model.TaskStatus
import com.demo.taskmanager.domain.model.TaskSummary

/**
 * Offset-based [PagingSource] for the tasks list.
 * Each page key is the zero-based page index; [PageDto.last] signals the final page.
 */
class TasksPagingSource(
    private val repository: TaskRepository,
    private val filters: TasksFilterState,
) : PagingSource<Int, TaskSummary>() {

    override fun getRefreshKey(state: PagingState<Int, TaskSummary>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { page ->
                page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
            }
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TaskSummary> {
        val page = params.key ?: 0
        return when (val result = repository.getTasks(
            page = page,
            size = params.loadSize,
            projectId = filters.projectId,
            userId = filters.assignedUserId,
            status = filters.status?.toDto(),
            completionStatus = filters.completionStatus,
        )) {
            is NetworkResult.Success -> LoadResult.Page(
                data = result.data.content.map { it.toDomain() },
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (result.data.last) null else page + 1,
            )
            is NetworkResult.Error -> LoadResult.Error(Exception("HTTP ${result.code}"))
            is NetworkResult.Exception -> LoadResult.Error(result.throwable)
        }
    }
}

/** Maps domain [TaskStatus] to its DTO counterpart for API filter params. */
private fun TaskStatus.toDto(): DtoTaskStatus = when (this) {
    TaskStatus.TODO -> DtoTaskStatus.TODO
    TaskStatus.IN_PROGRESS -> DtoTaskStatus.IN_PROGRESS
    TaskStatus.DONE -> DtoTaskStatus.DONE
}
