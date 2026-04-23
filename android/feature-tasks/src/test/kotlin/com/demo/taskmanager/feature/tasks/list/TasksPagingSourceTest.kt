package com.demo.taskmanager.feature.tasks.list

import androidx.paging.PagingSource
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.PageDto
import com.demo.taskmanager.data.dto.TaskSummaryDto
import com.demo.taskmanager.data.dto.enums.TaskStatus as DtoStatus
import com.demo.taskmanager.data.repo.TaskRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TasksPagingSourceTest {

    private val repository = mockk<TaskRepository>()
    private val filters = TasksFilterState()

    private fun buildSource() = TasksPagingSource(repository, filters)

    private fun refreshParams(key: Int? = null) =
        PagingSource.LoadParams.Refresh(key = key, loadSize = 20, placeholdersEnabled = false)

    private fun summaryDto(id: String = "1") = TaskSummaryDto(
        id = id,
        taskCode = "T-$id",
        title = "Task $id",
        description = null,
        status = DtoStatus.TODO,
        type = null,
        progress = 0,
        assignedUserId = null,
        assignedUserName = null,
        projectId = null,
        projectName = null,
        phaseId = null,
        phaseName = null,
    )

    private fun pageDto(
        content: List<TaskSummaryDto>,
        page: Int = 0,
        last: Boolean = true,
    ) = PageDto(
        content = content,
        page = page,
        size = content.size,
        totalElements = content.size.toLong(),
        totalPages = if (last) 1 else 3,
        last = last,
    )

    @Test
    fun `first page with last=true has null prev and null next key`() = runTest {
        coEvery { repository.getTasks(any(), any(), any(), any(), any(), any()) } returns
            NetworkResult.Success(pageDto(listOf(summaryDto()), last = true))

        val result = buildSource().load(refreshParams()) as PagingSource.LoadResult.Page

        assertEquals(1, result.data.size)
        assertEquals("Task 1", result.data.first().title)
        assertNull(result.prevKey)
        assertNull(result.nextKey)
    }

    @Test
    fun `first page with last=false emits next key 1`() = runTest {
        coEvery { repository.getTasks(any(), any(), any(), any(), any(), any()) } returns
            NetworkResult.Success(pageDto(listOf(summaryDto()), last = false))

        val result = buildSource().load(refreshParams()) as PagingSource.LoadResult.Page

        assertNull(result.prevKey)
        assertEquals(1, result.nextKey)
    }

    @Test
    fun `non-first page has prev key`() = runTest {
        coEvery { repository.getTasks(any(), any(), any(), any(), any(), any()) } returns
            NetworkResult.Success(pageDto(listOf(summaryDto()), page = 2, last = true))

        val result = buildSource().load(refreshParams(key = 2)) as PagingSource.LoadResult.Page

        assertEquals(1, result.prevKey)
        assertNull(result.nextKey)
    }

    @Test
    fun `HTTP error maps to LoadResult Error`() = runTest {
        coEvery { repository.getTasks(any(), any(), any(), any(), any(), any()) } returns
            NetworkResult.Error(500, null)

        val result = buildSource().load(refreshParams())

        assert(result is PagingSource.LoadResult.Error)
        assertEquals("HTTP 500", (result as PagingSource.LoadResult.Error).throwable.message)
    }

    @Test
    fun `network exception propagates as LoadResult Error`() = runTest {
        val cause = RuntimeException("Network unreachable")
        coEvery { repository.getTasks(any(), any(), any(), any(), any(), any()) } returns
            NetworkResult.Exception(cause)

        val result = buildSource().load(refreshParams())

        assert(result is PagingSource.LoadResult.Error)
        assertEquals(cause, (result as PagingSource.LoadResult.Error).throwable)
    }
}
