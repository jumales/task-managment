package com.demo.taskmanager.data.repo

import com.demo.taskmanager.data.api.TaskApi
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.PageDto
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.dto.TaskSummaryDto
import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.dto.enums.TaskType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

/** Unit tests for [TaskRepository] covering success, HTTP error, and network failure paths. */
class TaskRepositoryTest {

    private val api: TaskApi = mockk()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val repo = TaskRepository(api, json)

    // ── getTasks ───────────────────────────────────────────────────────────────

    @Test
    fun `getTasks - success wraps page in NetworkResult_Success`() = runTest {
        coEvery { api.getTasks(any(), any(), any(), any(), any(), any()) } returns page(taskSummary())

        val result = repo.getTasks()

        assertInstanceOf(NetworkResult.Success::class.java, result)
        assertEquals(1, (result as NetworkResult.Success).data.content.size)
    }

    @Test
    fun `getTasks - 404 returns NetworkResult_Error with correct code`() = runTest {
        coEvery { api.getTasks(any(), any(), any(), any(), any(), any()) } throws httpError(404)

        val result = repo.getTasks()

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertEquals(404, (result as NetworkResult.Error).code)
    }

    @Test
    fun `getTasks - 500 returns NetworkResult_Error with correct code`() = runTest {
        coEvery { api.getTasks(any(), any(), any(), any(), any(), any()) } throws httpError(500)

        val result = repo.getTasks()

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertEquals(500, (result as NetworkResult.Error).code)
    }

    @Test
    fun `getTasks - timeout returns NetworkResult_Exception`() = runTest {
        coEvery { api.getTasks(any(), any(), any(), any(), any(), any()) } throws SocketTimeoutException("timeout")

        val result = repo.getTasks()

        assertInstanceOf(NetworkResult.Exception::class.java, result)
    }

    // ── getTask ────────────────────────────────────────────────────────────────

    @Test
    fun `getTask - success wraps DTO in NetworkResult_Success`() = runTest {
        coEvery { api.getTask("task-1") } returns taskDto()

        val result = repo.getTask("task-1")

        assertInstanceOf(NetworkResult.Success::class.java, result)
    }

    @Test
    fun `getTask - 404 returns NetworkResult_Error`() = runTest {
        coEvery { api.getTask(any()) } throws httpError(404)

        val result = repo.getTask("missing")

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertEquals(404, (result as NetworkResult.Error).code)
    }

    // ── getProjects ────────────────────────────────────────────────────────────

    @Test
    fun `getProjects - success returns page`() = runTest {
        coEvery { api.getProjects(any(), any(), any()) } returns PageDto(
            content = listOf(projectDto()),
            page = 0, size = 50, totalElements = 1, totalPages = 1, last = true,
        )

        val result = repo.getProjects()

        assertInstanceOf(NetworkResult.Success::class.java, result)
        assertEquals(1, (result as NetworkResult.Success).data.content.size)
    }

    @Test
    fun `getProjects - network error returns NetworkResult_Exception`() = runTest {
        coEvery { api.getProjects(any(), any(), any()) } throws RuntimeException("no network")

        val result = repo.getProjects()

        assertInstanceOf(NetworkResult.Exception::class.java, result)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())),
    )

    private fun page(vararg items: TaskSummaryDto) = PageDto(
        content = items.toList(),
        page = 0, size = 20, totalElements = items.size.toLong(), totalPages = 1, last = true,
    )

    private fun taskSummary() = TaskSummaryDto(
        id = "task-1",
        taskCode = "CODE_1",
        title = "Fix login bug",
        description = null,
        status = TaskStatus.TODO,
        type = TaskType.BUG_FIXING,
        progress = 0,
        assignedUserId = null,
        assignedUserName = null,
        projectId = "proj-1",
        projectName = "Backend",
        phaseId = null,
        phaseName = null,
    )

    private fun taskDto() = com.demo.taskmanager.data.dto.TaskDto(
        id = "task-1",
        taskCode = "CODE_1",
        title = "Fix login bug",
        description = null,
        status = TaskStatus.TODO,
        type = null,
        progress = 0,
        participants = emptyList(),
        project = null,
        phase = null,
        version = 1L,
    )

    private fun projectDto() = ProjectDto(
        id = "proj-1",
        name = "Backend",
        description = null,
        taskCodePrefix = "BE_",
        defaultPhaseId = null,
    )
}
