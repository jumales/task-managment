package com.demo.taskmanager.feature.reports

/** Generic loading-state wrapper shared across all report screens. */
sealed class ReportUiState<out T> {
    data object Loading : ReportUiState<Nothing>()
    /** Not yet requested — used as initial state before the user triggers a load. */
    data object Idle : ReportUiState<Nothing>()
    data class Success<T>(val data: T) : ReportUiState<T>()
    data class Error(val message: String) : ReportUiState<Nothing>()
}
