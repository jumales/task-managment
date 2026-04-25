package com.demo.taskmanager.domain.usecase

import com.demo.taskmanager.domain.model.User
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Contract tests verifying [GetUsersUseCase] and [GetUserUseCase] interface semantics. */
class UserUseCasesTest {

    // ── GetUsersUseCase ────────────────────────────────────────────────────────

    @Test
    fun `GetUsersUseCase - returns list from implementation`() = runTest {
        val users = listOf(user("u1"), user("u2"), user("u3"))
        val useCase: GetUsersUseCase = GetUsersUseCase { users }

        val result = useCase()

        assertEquals(3, result.size)
        assertEquals("u1", result[0].id)
    }

    @Test
    fun `GetUsersUseCase - returns empty list when no users`() = runTest {
        val useCase: GetUsersUseCase = GetUsersUseCase { emptyList() }

        assertTrue(useCase().isEmpty())
    }

    @Test
    fun `GetUsersUseCase - propagates network exception`() = runTest {
        val useCase: GetUsersUseCase = GetUsersUseCase { throw RuntimeException("timeout") }

        assertThrows<RuntimeException> { useCase() }
    }

    // ── GetUserUseCase ─────────────────────────────────────────────────────────

    @Test
    fun `GetUserUseCase - returns user by id`() = runTest {
        val useCase: GetUserUseCase = GetUserUseCase { id -> user(id) }

        val result = useCase("user-7")

        assertEquals("user-7", result.id)
    }

    @Test
    fun `GetUserUseCase - propagates exception for unknown id`() = runTest {
        val useCase: GetUserUseCase = GetUserUseCase { throw NoSuchElementException("user not found") }

        assertThrows<NoSuchElementException> { useCase("ghost") }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun user(id: String = "u-1") = User(
        id = id,
        name = "User $id",
        email = "$id@example.com",
        username = id,
        active = true,
        avatarFileId = null,
        language = "en",
    )
}
