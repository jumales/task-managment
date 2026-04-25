package com.demo.taskmanager.data.repo

import com.demo.taskmanager.data.api.UserApi
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.PageDto
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.UserRoleDto
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

/** Unit tests for [UserRepository] covering success, HTTP error, and network failure paths. */
class UserRepositoryTest {

    private val api: UserApi = mockk()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val repo = UserRepository(api, json)

    // ── getUsers ───────────────────────────────────────────────────────────────

    @Test
    fun `getUsers - success wraps page in NetworkResult_Success`() = runTest {
        coEvery { api.getUsers(any(), any()) } returns page(userDto("u1"), userDto("u2"))

        val result = repo.getUsers()

        assertInstanceOf(NetworkResult.Success::class.java, result)
        assertEquals(2, (result as NetworkResult.Success).data.content.size)
    }

    @Test
    fun `getUsers - 401 returns NetworkResult_Error`() = runTest {
        coEvery { api.getUsers(any(), any()) } throws httpError(401)

        val result = repo.getUsers()

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertEquals(401, (result as NetworkResult.Error).code)
    }

    @Test
    fun `getUsers - 500 returns NetworkResult_Error`() = runTest {
        coEvery { api.getUsers(any(), any()) } throws httpError(500)

        val result = repo.getUsers()

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertEquals(500, (result as NetworkResult.Error).code)
    }

    @Test
    fun `getUsers - timeout returns NetworkResult_Exception`() = runTest {
        coEvery { api.getUsers(any(), any()) } throws SocketTimeoutException("read timeout")

        val result = repo.getUsers()

        assertInstanceOf(NetworkResult.Exception::class.java, result)
    }

    // ── getMe ──────────────────────────────────────────────────────────────────

    @Test
    fun `getMe - success wraps UserDto`() = runTest {
        coEvery { api.getMe() } returns userDto("me")

        val result = repo.getMe()

        assertInstanceOf(NetworkResult.Success::class.java, result)
        assertEquals("me", (result as NetworkResult.Success).data.id)
    }

    @Test
    fun `getMe - 403 returns NetworkResult_Error`() = runTest {
        coEvery { api.getMe() } throws httpError(403)

        val result = repo.getMe()

        assertInstanceOf(NetworkResult.Error::class.java, result)
        assertEquals(403, (result as NetworkResult.Error).code)
    }

    // ── getUserRoles ───────────────────────────────────────────────────────────

    @Test
    fun `getUserRoles - success returns role list`() = runTest {
        coEvery { api.getUserRoles("u1") } returns UserRoleDto(roles = listOf("ADMIN", "USER"))

        val result = repo.getUserRoles("u1")

        assertInstanceOf(NetworkResult.Success::class.java, result)
        assertEquals(listOf("ADMIN", "USER"), (result as NetworkResult.Success).data.roles)
    }

    @Test
    fun `setUserRoles - network error returns NetworkResult_Exception`() = runTest {
        coEvery { api.setUserRoles(any(), any()) } throws RuntimeException("offline")

        val result = repo.setUserRoles("u1", UserRoleDto(listOf("USER")))

        assertInstanceOf(NetworkResult.Exception::class.java, result)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())),
    )

    private fun page(vararg users: UserDto) = PageDto(
        content = users.toList(),
        page = 0, size = 20, totalElements = users.size.toLong(), totalPages = 1, last = true,
    )

    private fun userDto(id: String = "u-1") = UserDto(
        id = id,
        name = "User $id",
        email = "$id@example.com",
        username = id,
        active = true,
        avatarFileId = null,
        language = "en",
    )
}
