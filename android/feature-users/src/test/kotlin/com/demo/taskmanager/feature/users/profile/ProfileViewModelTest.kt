package com.demo.taskmanager.feature.users.profile

import android.content.Context
import android.content.Intent
import app.cash.turbine.test
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.PresignedUrlDto
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.UserUpdateRequest
import com.demo.taskmanager.data.repo.FileRepository
import com.demo.taskmanager.data.repo.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var fileRepository: FileRepository
    private lateinit var authManager: AuthManager
    private lateinit var context: Context

    private val sampleUser = UserDto(
        id = "u-1",
        name = "Alice",
        email = "alice@example.com",
        username = "alice",
        active = true,
        avatarFileId = null,
        language = "en",
    )

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        fileRepository = mockk()
        authManager = mockk()
        context = mockk(relaxed = true)

        every { authManager.authState } returns MutableStateFlow(
            AuthState.Authenticated(userId = "u-1", roles = listOf("USER"))
        )
    }

    private fun buildViewModel(): ProfileViewModel {
        coEvery { userRepository.getMe() } returns NetworkResult.Success(sampleUser)
        return ProfileViewModel(userRepository, fileRepository, authManager, context)
    }

    @Test
    fun `load success transitions to Loaded state`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ProfileUiState.Loaded)
        assertEquals("Alice", (state as ProfileUiState.Loaded).user.name)
    }

    @Test
    fun `load fetches presigned URL when avatarFileId is set`() = runTest(dispatcher) {
        coEvery { userRepository.getMe() } returns NetworkResult.Success(
            sampleUser.copy(avatarFileId = "file-42")
        )
        coEvery { fileRepository.getPresignedUrl("file-42") } returns
            NetworkResult.Success(PresignedUrlDto("https://cdn.example.com/avatar.jpg"))

        val vm = ProfileViewModel(userRepository, fileRepository, authManager, context)
        advanceUntilIdle()

        assertEquals(
            "https://cdn.example.com/avatar.jpg",
            (vm.uiState.value as ProfileUiState.Loaded).avatarUrl,
        )
    }

    @Test
    fun `changeLanguage calls API and emits locale event`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        coEvery { userRepository.updateLanguage("u-1", "hr") } returns
            NetworkResult.Success(sampleUser.copy(language = "hr"))

        vm.localeEvent.test {
            vm.changeLanguage("hr")
            advanceUntilIdle()
            assertEquals("hr", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { userRepository.updateLanguage("u-1", "hr") }
    }

    @Test
    fun `changeLanguage API error shows snackbar and does not emit locale event`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        coEvery { userRepository.updateLanguage("u-1", "hr") } returns
            NetworkResult.Error(500, null)

        vm.changeLanguage("hr")
        advanceUntilIdle()

        val state = vm.uiState.value as ProfileUiState.Loaded
        assertTrue(state.snackbarMessage?.contains("500") == true)
    }

    @Test
    fun `updateName persists new name`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        coEvery { userRepository.updateUser("u-1", UserUpdateRequest(name = "Bob")) } returns
            NetworkResult.Success(sampleUser.copy(name = "Bob"))

        vm.updateName("Bob")
        advanceUntilIdle()

        assertEquals("Bob", (vm.uiState.value as ProfileUiState.Loaded).user.name)
    }

    @Test
    fun `logout triggers logoutEvent intent`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        val fakeIntent = mockk<Intent>()
        val callbackSlot = slot<(Intent) -> Unit>()
        every { authManager.buildLogoutIntent(capture(callbackSlot)) } answers {
            callbackSlot.captured(fakeIntent)
        }

        vm.logoutEvent.test {
            vm.logout()
            advanceUntilIdle()
            assertEquals(fakeIntent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
