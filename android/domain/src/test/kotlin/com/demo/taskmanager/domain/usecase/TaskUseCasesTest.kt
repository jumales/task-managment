package com.demo.taskmanager.domain.usecase

import com.demo.taskmanager.domain.model.Task
import com.demo.taskmanager.domain.model.TaskStatus
import com.demo.taskmanager.domain.model.TaskSummary
import com.demo.taskmanager.domain.model.TaskType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Contract tests verifying [GetTasksUseCase] and [GetTaskUseCase] interface semantics. */
class TaskUseCasesTest {

    // ── GetTasksUseCase ────────────────────────────────────────────────────────

    @Test
    fun `GetTasksUseCase - passes page and projectId parameters correctly`() = runTest {
        var capturedPage = -1
        var capturedProjectId: String? = "unset"

        val useCase: GetTasksUseCase = object : GetTasksUseCase {
            override suspend fun invoke(
                page: Int, size: Int, projectId: String?, userId: String?, status: TaskStatus?,
            ): List<TaskSummary> {
                capturedPage = page
                capturedProjectId = projectId
                return emptyList()
            }
        }

        useCase(page = 2, size = 20, projectId = "proj-1")

        assertEquals(2, capturedPage)
        assertEquals("proj-1", capturedProjectId)
    }

    @Test
    fun `GetTasksUseCase - defaults are page 0 and size 20`() = runTest {
        var capturedPage = -1
        var capturedSize = -1

        val useCase: GetTasksUseCase = object : GetTasksUseCase {
            override suspend fun invoke(
                page: Int, size: Int, projectId: String?, userId: String?, status: TaskStatus?,
            ): List<TaskSummary> {
                capturedPage = page
                capturedSize = size
                return emptyList()
            }
        }

        useCase()

        assertEquals(0, capturedPage)
        assertEquals(20, capturedSize)
    }

    @Test
    fun `GetTasksUseCase - status filter is forwarded`() = runTest {
        var capturedStatus: TaskStatus? = null

        val useCase: GetTasksUseCase = object : GetTasksUseCase {
            override suspend fun invoke(
                page: Int, size: Int, projectId: String?, userId: String?, status: TaskStatus?,
            ): List<TaskSummary> {
                capturedStatus = status
                return emptyList()
            }
        }

        useCase(status = TaskStatus.IN_PROGRESS)

        assertEquals(TaskStatus.IN_PROGRESS, capturedStatus)
    }

    @Test
    fun `GetTasksUseCase - exception propagates to caller`() = runTest {
        val useCase: GetTasksUseCase = object : GetTasksUseCase {
            override suspend fun invoke(
                page: Int, size: Int, projectId: String?, userId: String?, status: TaskStatus?,
            ): List<TaskSummary> = throw RuntimeException("backend unreachable")
        }

        assertThrows<RuntimeException> { useCase() }
    }

    // ── GetTaskUseCase ─────────────────────────────────────────────────────────

    @Test
    fun `GetTaskUseCase - returns task matching the requested id`() = runTest {
        val useCase: GetTaskUseCase = GetTaskUseCase { id ->
            task(id = id)
        }

        val result = useCase("task-42")

        assertEquals("task-42", result.id)
    }

    @Test
    fun `GetTaskUseCase - propagates exception for unknown id`() = runTest {
        val useCase: GetTaskUseCase = GetTaskUseCase { throw NoSuchElementException("task not found") }

        assertThrows<NoSuchElementException> { useCase("unknown") }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun task(id: String = "t-1") = Task(
        id = id,
        taskCode = "CODE_1",
        title = "Sample task",
        description = null,
        status = TaskStatus.TODO,
        type = TaskType.FEATURE,
        progress = 0,
        project = null,
        phase = null,
        version = 1L,
    )
}
