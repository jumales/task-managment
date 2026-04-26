package com.demo.taskmanager.feature.tasks.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.demo.taskmanager.core.network.push.PushEventBus
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.CommentDto
import com.demo.taskmanager.data.dto.TaskFullDto
import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.repo.TaskRepository
import com.demo.taskmanager.domain.model.Comment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>()
    private val pushEventBus = mockk<PushEventBus>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { pushEventBus.flow } returns MutableSharedFlow()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(taskId: String = "task-1") =
        TaskDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("taskId" to taskId)),
            repository = repository,
            pushEventBus = pushEventBus,
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

    @Test
    fun `addComment inserts optimistic entry then replaces it on success`() = runTest {
        val task = fullDto()
        coEvery { repository.getTaskFull("task-1") } returns NetworkResult.Success(task)
        coEvery { repository.getComments("task-1") } returns NetworkResult.Success(emptyList())
        val returnedComment = CommentDto(
            id = "comment-real",
            userId = "user-1",
            userName = "Alice",
            content = "Hello",
            createdAt = "2024-01-01T00:00:00Z",
        )
        coEvery { repository.addComment("task-1", any()) } returns NetworkResult.Success(returnedComment)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.uiState.test {
            val initial = awaitItem() as TaskDetailUiState.Loaded
            assertEquals(0, initial.comments.size)

            vm.addComment("Hello")
            // Optimistic insert
            val optimistic = awaitItem() as TaskDetailUiState.Loaded
            assertEquals(1, optimistic.comments.size)
            assertEquals("Hello", optimistic.comments[0].content)

            advanceUntilIdle()
            // Real comment replaces optimistic
            val real = awaitItem() as TaskDetailUiState.Loaded
            assertEquals(1, real.comments.size)
            assertEquals("comment-real", real.comments[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addComment rolls back optimistic entry and sets snackbar on server error`() = runTest {
        val task = fullDto()
        coEvery { repository.getTaskFull("task-1") } returns NetworkResult.Success(task)
        coEvery { repository.getComments("task-1") } returns NetworkResult.Success(emptyList())
        coEvery { repository.addComment("task-1", any()) } returns NetworkResult.Error(500, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.uiState.test {
            val initial = awaitItem() as TaskDetailUiState.Loaded
            assertEquals(0, initial.comments.size)

            vm.addComment("Hello")
            // Optimistic insert
            val optimistic = awaitItem() as TaskDetailUiState.Loaded
            assertEquals(1, optimistic.comments.size)

            advanceUntilIdle()
            // Rollback: comment list reverts, snackbar shown
            val rolledBack = awaitItem() as TaskDetailUiState.Loaded
            assertEquals(0, rolledBack.comments.size)
            assertNotNull(rolledBack.snackbarMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearSnackbar removes snackbar message`() = runTest {
        val task = fullDto()
        coEvery { repository.getTaskFull("task-1") } returns NetworkResult.Success(task)
        coEvery { repository.getComments("task-1") } returns NetworkResult.Success(emptyList())
        coEvery { repository.addComment("task-1", any()) } returns NetworkResult.Error(500, null)

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.addComment("Hello")
        advanceUntilIdle()

        val withSnackbar = vm.uiState.value as TaskDetailUiState.Loaded
        assertNotNull(withSnackbar.snackbarMessage)

        vm.clearSnackbar()
        val cleared = vm.uiState.value as TaskDetailUiState.Loaded
        assertNull(cleared.snackbarMessage)
    }
}
