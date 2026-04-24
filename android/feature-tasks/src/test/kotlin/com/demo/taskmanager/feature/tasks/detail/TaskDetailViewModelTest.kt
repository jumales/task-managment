package com.demo.taskmanager.feature.tasks.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.TaskFullDto
import com.demo.taskmanager.data.dto.enums.TaskStatus
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(taskId: String = "task-1") =
        TaskDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("taskId" to taskId)),
            repository = repository,
        )

    private fun fullDto(id: String = "task-1") = TaskFullDto(
        id = id,
        taskCode = "T-1",
        title = "Test task",
        description = null,
        status = TaskStatus.TODO,
        type = null,
        progress = 0,
        participants = emptyList(),
        project = null,
        phase = null,
        assignedUser = null,
        timelines = emptyList(),
        plannedWork = emptyList(),
        bookedWork = emptyList(),
        version = 1L,
    )

    @Test
    fun `initial state is Loading`() = runTest {
        coEvery { repository.getTaskFull(any()) } returns NetworkResult.Success(fullDto())
        coEvery { repository.getComments(any()) } returns NetworkResult.Success(emptyList())

        val vm = buildViewModel()
        vm.uiState.test {
            assertInstanceOf(TaskDetailUiState.Loading::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transitions Loading to Loaded when repository succeeds`() = runTest {
        val task = fullDto()
        coEvery { repository.getTaskFull("task-1") } returns NetworkResult.Success(task)
        coEvery { repository.getComments("task-1") } returns NetworkResult.Success(emptyList())

        val vm = buildViewModel()
        vm.uiState.test {
            assertInstanceOf(TaskDetailUiState.Loading::class.java, awaitItem())
            advanceUntilIdle()
            val loaded = awaitItem() as TaskDetailUiState.Loaded
            assertEquals(task.id, loaded.task.id)
            assertEquals(emptyList<Any>(), loaded.comments)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transitions Loading to Error when task fetch fails`() = runTest {
        coEvery { repository.getTaskFull(any()) } returns NetworkResult.Error(404, null)
        coEvery { repository.getComments(any()) } returns NetworkResult.Success(emptyList())

        val vm = buildViewModel()
        vm.uiState.test {
            assertInstanceOf(TaskDetailUiState.Loading::class.java, awaitItem())
            advanceUntilIdle()
            assertInstanceOf(TaskDetailUiState.Error::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reload resets to Loading then emits Loaded`() = runTest {
        val task = fullDto()
        coEvery { repository.getTaskFull("task-1") } returns NetworkResult.Success(task)
        coEvery { repository.getComments("task-1") } returns NetworkResult.Success(emptyList())

        val vm = buildViewModel()
        advanceUntilIdle() // drain initial load

        vm.uiState.test {
            awaitItem() // Loaded from initial load

            vm.reload()
            assertInstanceOf(TaskDetailUiState.Loading::class.java, awaitItem())
            advanceUntilIdle()
            assertInstanceOf(TaskDetailUiState.Loaded::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTaskFull and getComments called exactly once on init`() = runTest {
        coEvery { repository.getTaskFull(any()) } returns NetworkResult.Success(fullDto())
        coEvery { repository.getComments(any()) } returns NetworkResult.Success(emptyList())

        buildViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getTaskFull("task-1") }
        coVerify(exactly = 1) { repository.getComments("task-1") }
    }
}
