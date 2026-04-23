package com.demo.taskmanager.feature.tasks.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.demo.taskmanager.data.dto.enums.TaskCompletionStatus
import com.demo.taskmanager.data.repo.TaskRepository
import com.demo.taskmanager.domain.model.TaskStatus
import com.demo.taskmanager.domain.model.TaskSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val PAGE_SIZE = 20

/**
 * ViewModel for the tasks list screen.
 * Recreates the [Pager] whenever [filters] changes via [flatMapLatest],
 * then caches the resulting [PagingData] in [viewModelScope] to survive recomposition.
 */
@HiltViewModel
class TasksListViewModel @Inject constructor(
    private val repository: TaskRepository,
) : ViewModel() {

    private val _filters = MutableStateFlow(TasksFilterState())
    val filters: StateFlow<TasksFilterState> = _filters.asStateFlow()

    val tasks: Flow<PagingData<TaskSummary>> = _filters
        .flatMapLatest { createPager(it).flow }
        .cachedIn(viewModelScope)

    /** Sets the status filter; pass null to clear. */
    fun setStatusFilter(status: TaskStatus?) {
        _filters.update { it.copy(status = status) }
    }

    /** Sets the completion-status filter; pass null to clear. */
    fun setCompletionStatusFilter(completionStatus: TaskCompletionStatus?) {
        _filters.update { it.copy(completionStatus = completionStatus) }
    }

    /** Sets the project filter; pass null to clear. */
    fun setProjectFilter(projectId: String?) {
        _filters.update { it.copy(projectId = projectId) }
    }

    /** Sets the assigned-user filter; pass null to clear. */
    fun setAssignedUserFilter(userId: String?) {
        _filters.update { it.copy(assignedUserId = userId) }
    }

    private fun createPager(filters: TasksFilterState) = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { TasksPagingSource(repository, filters) },
    )
}
