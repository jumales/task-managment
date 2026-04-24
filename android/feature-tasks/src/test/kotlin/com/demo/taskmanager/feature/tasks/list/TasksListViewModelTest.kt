package com.demo.taskmanager.feature.tasks.list

import app.cash.turbine.test
import com.demo.taskmanager.data.dto.enums.TaskCompletionStatus
import com.demo.taskmanager.data.repo.TaskRepository
import com.demo.taskmanager.domain.model.TaskStatus
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasksListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TasksListViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = TasksListViewModel(mockk(relaxed = true))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial filter state is empty`() = runTest {
        viewModel.filters.test {
            assertEquals(TasksFilterState(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setStatusFilter updates status and clears on null`() = runTest {
        viewModel.filters.test {
            awaitItem() // initial

            viewModel.setStatusFilter(TaskStatus.IN_PROGRESS)
            assertEquals(TaskStatus.IN_PROGRESS, awaitItem().status)

            viewModel.setStatusFilter(null)
            assertNull(awaitItem().status)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCompletionStatusFilter updates completionStatus`() = runTest {
        viewModel.filters.test {
            awaitItem() // initial

            viewModel.setCompletionStatusFilter(TaskCompletionStatus.FINISHED)
            assertEquals(TaskCompletionStatus.FINISHED, awaitItem().completionStatus)

            viewModel.setCompletionStatusFilter(null)
            assertNull(awaitItem().completionStatus)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setProjectFilter updates projectId`() = runTest {
        viewModel.filters.test {
            awaitItem()

            viewModel.setProjectFilter("proj-1")
            assertEquals("proj-1", awaitItem().projectId)

            viewModel.setProjectFilter(null)
            assertNull(awaitItem().projectId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple filter changes accumulate without losing other fields`() = runTest {
        viewModel.filters.test {
            awaitItem()

            viewModel.setStatusFilter(TaskStatus.TODO)
            val afterStatus = awaitItem()
            assertEquals(TaskStatus.TODO, afterStatus.status)

            viewModel.setProjectFilter("proj-42")
            val afterProject = awaitItem()
            assertEquals(TaskStatus.TODO, afterProject.status)
            assertEquals("proj-42", afterProject.projectId)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
