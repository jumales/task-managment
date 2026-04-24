package com.demo.taskmanager.feature.tasks.create

import app.cash.turbine.test
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.PageDto
import com.demo.taskmanager.data.dto.TaskDto
import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.repo.TaskRepository
import com.demo.taskmanager.data.repo.UserRepository
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCreateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val taskRepository = mockk<TaskRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { taskRepository.getProjects(any(), any()) } returns NetworkResult.Success(
            PageDto(content = emptyList(), page = 0, size = 200, totalElements = 0, totalPages = 0, last = true)
        )
        coEvery { userRepository.getUsers(any(), any()) } returns NetworkResult.Success(
            PageDto(content = emptyList(), page = 0, size = 200, totalElements = 0, totalPages = 0, last = true)
        )
        coEvery { taskRepository.getPhases(any()) } returns NetworkResult.Success(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = TaskCreateViewModel(
        taskRepository = taskRepository,
        userRepository = userRepository,
    )

    @Test
    fun `initial state is Idle`() = runTest {
        val vm = buildViewModel()
        vm.uiState.test {
            assertInstanceOf(TaskCreateUiState.Idle::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit does not call createTask when title is blank`() = runTest {
        val vm = buildViewModel()
        vm.onProjectSelected("project-1")
        vm.submit()
        advanceUntilIdle()

        coVerify(exactly = 0) { taskRepository.createTask(any()) }
        assertTrue(vm.uiState.value is TaskCreateUiState.Idle)
    }

    @Test
    fun `submit does not call createTask when project is not selected`() = runTest {
        val vm = buildViewModel()
        vm.onTitleChange("My Task")
        vm.submit()
        advanceUntilIdle()

        coVerify(exactly = 0) { taskRepository.createTask(any()) }
    }

    @Test
    fun `submit transitions to Success when repository returns task`() = runTest {
        val taskDto = taskDto()
        coEvery { taskRepository.createTask(any()) } returns NetworkResult.Success(taskDto)

        val vm = buildViewModel()
        vm.onTitleChange("My Task")
        vm.onProjectSelected("project-1")

        vm.uiState.test {
            awaitItem() // Idle
            vm.submit()
            assertInstanceOf(TaskCreateUiState.Submitting::class.java, awaitItem())
            advanceUntilIdle()
            val success = awaitItem()
            assertInstanceOf(TaskCreateUiState.Success::class.java, success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit transitions to Error on HTTP failure`() = runTest {
        coEvery { taskRepository.createTask(any()) } returns NetworkResult.Error(400, null)

        val vm = buildViewModel()
        vm.onTitleChange("My Task")
        vm.onProjectSelected("project-1")

        vm.uiState.test {
            awaitItem() // Idle
            vm.submit()
            assertInstanceOf(TaskCreateUiState.Submitting::class.java, awaitItem())
            advanceUntilIdle()
            assertInstanceOf(TaskCreateUiState.Error::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `titleError is non-null when title is blank`() = runTest {
        val vm = buildViewModel()
        assertTrue(vm.titleError != null)
        vm.onTitleChange("My Task")
        assertTrue(vm.titleError == null)
    }

    private fun taskDto() = TaskDto(
        id = "task-1",
        taskCode = "T-1",
        title = "My Task",
        description = null,
        status = TaskStatus.TODO,
        type = null,
        progress = 0,
        participants = emptyList(),
        project = null,
        phase = null,
        version = 1L,
    )
}
