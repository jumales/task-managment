package com.demo.taskmanager.feature.reports.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.MyTaskReportDto
import com.demo.taskmanager.data.repo.ReportingRepository
import com.demo.taskmanager.feature.reports.ReportUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Day-range filter options for the my-tasks report. */
enum class DayRange(val days: Int?, val label: String) {
    ALL(null, "All"),
    WEEK(7, "7d"),
    MONTH(30, "30d"),
    QUARTER(90, "90d"),
}

/**
 * Loads and filters the current user's open tasks via the reporting API.
 * Re-fetches on every [setRange] call.
 */
@HiltViewModel
class MyTasksViewModel @Inject constructor(
    private val repository: ReportingRepository,
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(DayRange.ALL)
    val selectedRange: StateFlow<DayRange> = _selectedRange.asStateFlow()

    private val _uiState = MutableStateFlow<ReportUiState<List<MyTaskReportDto>>>(ReportUiState.Loading)
    val uiState: StateFlow<ReportUiState<List<MyTaskReportDto>>> = _uiState.asStateFlow()

    init { load() }

    /** Switches the day-range filter and reloads data. */
    fun setRange(range: DayRange) {
        _selectedRange.value = range
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            _uiState.value = when (val r = repository.getMyTasks(_selectedRange.value.days)) {
                is NetworkResult.Success   -> ReportUiState.Success(r.data)
                is NetworkResult.Error     -> ReportUiState.Error(r.error?.message ?: "Failed to load tasks")
                is NetworkResult.Exception -> ReportUiState.Error(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }
}
