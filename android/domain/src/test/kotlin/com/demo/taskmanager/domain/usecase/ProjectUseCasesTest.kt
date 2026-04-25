package com.demo.taskmanager.domain.usecase

import com.demo.taskmanager.domain.model.Project
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Contract tests verifying [GetProjectsUseCase] and [GetProjectUseCase] interface semantics. */
class ProjectUseCasesTest {

    // ── GetProjectsUseCase ─────────────────────────────────────────────────────

    @Test
    fun `GetProjectsUseCase - returns list from implementation`() = runTest {
        val projects = listOf(project("p1"), project("p2"))
        val useCase: GetProjectsUseCase = GetProjectsUseCase { projects }

        val result = useCase()

        assertEquals(2, result.size)
        assertEquals("p1", result[0].id)
    }

    @Test
    fun `GetProjectsUseCase - returns empty list when no projects`() = runTest {
        val useCase: GetProjectsUseCase = GetProjectsUseCase { emptyList() }

        assertTrue(useCase().isEmpty())
    }

    @Test
    fun `GetProjectsUseCase - propagates exception`() = runTest {
        val useCase: GetProjectsUseCase = GetProjectsUseCase { throw IllegalStateException("no network") }

        assertThrows<IllegalStateException> { useCase() }
    }

    // ── GetProjectUseCase ──────────────────────────────────────────────────────

    @Test
    fun `GetProjectUseCase - returns project by id`() = runTest {
        val useCase: GetProjectUseCase = GetProjectUseCase { id -> project(id) }

        val result = useCase("proj-99")

        assertEquals("proj-99", result.id)
    }

    @Test
    fun `GetProjectUseCase - propagates exception for unknown id`() = runTest {
        val useCase: GetProjectUseCase = GetProjectUseCase { throw NoSuchElementException("not found") }

        assertThrows<NoSuchElementException> { useCase("unknown") }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun project(id: String = "p-1") = Project(
        id = id,
        name = "Project $id",
        description = null,
        taskCodePrefix = "CODE_",
        defaultPhaseId = null,
    )
}
