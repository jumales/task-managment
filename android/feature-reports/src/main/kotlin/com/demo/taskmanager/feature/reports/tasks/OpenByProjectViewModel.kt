package com.demo.taskmanager.feature.reports.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.ProjectOpenTaskCountDto
import com.demo.taskmanager.data.repo.ReportingRepository
import com.demo.taskmanager.feature.reports.ReportUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Loads open task counts per project from the reporting API. */
@HiltViewModel
class OpenByProjectViewModel @Inject constructor(
    private val repository: ReportingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportUiState<List<ProjectOpenTaskCountDto>>>(ReportUiState.Loading)
    val uiState: StateFlow<ReportUiState<List<ProjectOpenTaskCountDto>>> = _uiState.asStateFlow()

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            _uiState.value = when (val r = repository.getOpenTasksByProject()) {
                is NetworkResult.Success   -> ReportUiState.Success(r.data)
                is NetworkResult.Error     -> ReportUiState.Error(r.error?.message ?: "Failed to load")
                is NetworkResult.Exception -> ReportUiState.Error(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }
}
