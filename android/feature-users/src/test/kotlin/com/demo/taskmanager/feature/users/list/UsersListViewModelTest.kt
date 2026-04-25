package com.demo.taskmanager.feature.users.list

import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.UserRoleDto
import com.demo.taskmanager.data.repo.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsersListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val authManager = mockk<AuthManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authManager.authState } returns MutableStateFlow(
            AuthState.Authenticated(userId = "user-1", roles = emptyList()),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = UsersListViewModel(userRepository, authManager)

    @Test
    fun `initial state has isAdmin false for regular user`() {
        val vm = buildViewModel()

        assertFalse(vm.uiState.value.isAdmin)
    }

    @Test
    fun `initial state has isAdmin true for ADMIN role`() {
        every { authManager.authState } returns MutableStateFlow(
            AuthState.Authenticated(userId = "admin-1", roles = listOf("ADMIN")),
        )

        val vm = buildViewModel()

        assertTrue(vm.uiState.value.isAdmin)
    }

    @Test
    fun `initial state has no roleEditTarget`() {
        val vm = buildViewModel()

        assertNull(vm.uiState.value.roleEditTarget)
    }

    @Test
    fun `openRoleEditor - success populates roleEditTarget with loaded roles`() = runTest {
        val user = userDto()
        val rolesWithAdmin = user.copy(roles = listOf("ADMIN"))
        coEvery { userRepository.getUserRoles(user.id) } returns NetworkResult.Success(
            UserRoleDto(roles = listOf("ADMIN")),
        )
        val vm = buildViewModel()

        vm.openRoleEditor(user)
        advanceUntilIdle()

        assertEquals(rolesWithAdmin, vm.uiState.value.roleEditTarget)
    }

    @Test
    fun `openRoleEditor - HTTP error shows snackbar`() = runTest {
        coEvery { userRepository.getUserRoles(any()) } returns NetworkResult.Error(403, null)
        val vm = buildViewModel()

        vm.openRoleEditor(userDto())
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
        assertTrue(vm.uiState.value.snackbarMessage!!.contains("403"))
    }

    @Test
    fun `saveRoles - success clears roleEditTarget and shows confirmation`() = runTest {
        coEvery { userRepository.setUserRoles(any(), any()) } returns NetworkResult.Success(
            UserRoleDto(roles = listOf("USER")),
        )
        val vm = buildViewModel()

        vm.saveRoles("u-1", listOf("USER"))
        advanceUntilIdle()

        assertNull(vm.uiState.value.roleEditTarget)
        assertNotNull(vm.uiState.value.snackbarMessage)
        coVerify { userRepository.setUserRoles("u-1", UserRoleDto(listOf("USER"))) }
    }

    @Test
    fun `saveRoles - error shows snackbar`() = runTest {
        coEvery { userRepository.setUserRoles(any(), any()) } returns NetworkResult.Error(500, null)
        val vm = buildViewModel()

        vm.saveRoles("u-1", listOf("ADMIN"))
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `dismissRoleEditor clears roleEditTarget`() = runTest {
        coEvery { userRepository.getUserRoles(any()) } returns NetworkResult.Success(UserRoleDto(emptyList()))
        val vm = buildViewModel()
        vm.openRoleEditor(userDto())
        advanceUntilIdle()

        vm.dismissRoleEditor()

        assertNull(vm.uiState.value.roleEditTarget)
    }

    @Test
    fun `clearSnackbar removes message`() = runTest {
        coEvery { userRepository.getUserRoles(any()) } returns NetworkResult.Error(403, null)
        val vm = buildViewModel()
        vm.openRoleEditor(userDto())
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.snackbarMessage)

        vm.clearSnackbar()

        assertNull(vm.uiState.value.snackbarMessage)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun userDto(id: String = "u-1") = UserDto(
        id = id,
        name = "Alice",
        email = "alice@example.com",
        username = "alice",
        active = true,
        avatarFileId = null,
        language = "en",
    )
}
