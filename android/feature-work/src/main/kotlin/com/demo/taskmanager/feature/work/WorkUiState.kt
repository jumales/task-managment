package com.demo.taskmanager.feature.work

import com.demo.taskmanager.data.dto.BookedWorkDto
import com.demo.taskmanager.data.dto.PlannedWorkDto

/**
 * UI state for the Work tab: planned and booked work lists plus loading/error feedback.
 * Loading flag is separate so the list can be shown while a refresh is in flight.
 */
data class WorkUiState(
    val plannedWork: List<PlannedWorkDto> = emptyList(),
    val bookedWork: List<BookedWorkDto> = emptyList(),
    val isLoading: Boolean = true,
    /** Non-null while a snackbar should be shown; cleared after display. */
    val snackbarMessage: String? = null,
)
