package com.demo.taskmanager.feature.work

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.BookedWorkDto
import com.demo.taskmanager.data.dto.PlannedWorkDto
import com.demo.taskmanager.data.dto.enums.WorkType
import com.demo.taskmanager.data.repo.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getPlannedWork(any()) } returns NetworkResult.Success(emptyList())
        coEvery { repository.getBookedWork(any()) } returns NetworkResult.Success(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(taskId: String = "task-1") =
        WorkViewModel(
            savedStateHandle = SavedStateHandle(mapOf("taskId" to taskId)),
            repository = repository,
        )

    @Test
    fun `initial state is loading`() = runTest {
        val vm = buildViewModel()
        vm.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads planned and booked work in parallel on init`() = runTest {
        val planned = listOf(plannedWorkDto("pw-1"))
        val booked = listOf(bookedWorkDto("bw-1"))
        coEvery { repository.getPlannedWork("task-1") } returns NetworkResult.Success(planned)
        coEvery { repository.getBookedWork("task-1") } returns NetworkResult.Success(booked)

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.plannedWork.size)
        assertEquals(1, state.bookedWork.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `addBookedWork reloads list on success`() = runTest {
        coEvery { repository.addBookedWork("task-1", any()) } returns NetworkResult.Success(bookedWorkDto("new-1"))

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.addBookedWork(WorkType.DEVELOPMENT, 2L)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.addBookedWork("task-1", any()) }
        // reload called: getBookedWork invoked twice (init + after add)
        coVerify(exactly = 2) { repository.getBookedWork("task-1") }
    }

    @Test
    fun `addBookedWork sets snackbar on HTTP error`() = runTest {
        coEvery { repository.addBookedWork("task-1", any()) } returns NetworkResult.Error(422, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.addBookedWork(WorkType.DEVELOPMENT, 2L)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `deleteBookedWork reloads list on success`() = runTest {
        coEvery { repository.deleteBookedWork("task-1", "bw-1") } returns NetworkResult.Success(Unit)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deleteBookedWork("bw-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteBookedWork("task-1", "bw-1") }
        coVerify(exactly = 2) { repository.getBookedWork("task-1") }
    }

    @Test
    fun `updateBookedWork reloads list on success`() = runTest {
        coEvery { repository.updateBookedWork("task-1", "bw-1", any()) } returns
            NetworkResult.Success(bookedWorkDto("bw-1"))

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.updateBookedWork("bw-1", WorkType.TESTING, 3L)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateBookedWork("task-1", "bw-1", any()) }
    }

    @Test
    fun `addPlannedWork reloads list on success`() = runTest {
        coEvery { repository.addPlannedWork("task-1", any()) } returns NetworkResult.Success(plannedWorkDto("pw-new"))

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.addPlannedWork(WorkType.PLANNING, 4L)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.addPlannedWork("task-1", any()) }
        coVerify(exactly = 2) { repository.getPlannedWork("task-1") }
    }

    @Test
    fun `addPlannedWork sets snackbar on HTTP error`() = runTest {
        coEvery { repository.addPlannedWork("task-1", any()) } returns NetworkResult.Error(422, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.addPlannedWork(WorkType.PLANNING, 1L)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `clearSnackbar removes snackbar message`() = runTest {
        coEvery { repository.addBookedWork("task-1", any()) } returns NetworkResult.Error(500, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.addBookedWork(WorkType.DEVELOPMENT, 1L)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.snackbarMessage)

        vm.clearSnackbar()
        assertNull(vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `deleteBookedWork sets snackbar when phase is RELEASED — rejected by server 422`() = runTest {
        // Backend returns 422 when phase is in FINISHED_PHASES
        coEvery { repository.deleteBookedWork("task-1", any()) } returns NetworkResult.Error(422, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deleteBookedWork("bw-1")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
        // No reload triggered after error
        coVerify(exactly = 1) { repository.getBookedWork("task-1") }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun plannedWorkDto(id: String) = PlannedWorkDto(
        id = id,
        userId = "user-1",
        userName = "Alice",
        workType = WorkType.DEVELOPMENT,
        plannedHours = 4L,
        createdAt = "2024-01-01T00:00:00Z",
    )

    private fun bookedWorkDto(id: String) = BookedWorkDto(
        id = id,
        userId = "user-1",
        userName = "Alice",
        workType = WorkType.DEVELOPMENT,
        bookedHours = 2L,
        createdAt = "2024-01-01T00:00:00Z",
    )
}
