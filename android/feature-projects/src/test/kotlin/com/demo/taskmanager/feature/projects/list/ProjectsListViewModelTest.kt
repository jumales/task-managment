package com.demo.taskmanager.feature.projects.list

import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.ProjectCreateRequest
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.repo.TaskRepository
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
class ProjectsListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>(relaxed = true)
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

    private fun buildViewModel() = ProjectsListViewModel(repository, authManager)

    @Test
    fun `initial state has isAdmin false when user has no ADMIN role`() {
        val vm = buildViewModel()

        assertFalse(vm.uiState.value.isAdmin)
    }

    @Test
    fun `initial state has isAdmin true when user has ADMIN role`() {
        every { authManager.authState } returns MutableStateFlow(
            AuthState.Authenticated(userId = "admin-1", roles = listOf("ADMIN")),
        )

        val vm = buildViewModel()

        assertTrue(vm.uiState.value.isAdmin)
    }

    @Test
    fun `showCreateDialog sets flag to true`() {
        val vm = buildViewModel()

        vm.showCreateDialog()

        assertTrue(vm.uiState.value.showCreateDialog)
    }

    @Test
    fun `hideCreateDialog clears flag`() {
        val vm = buildViewModel()
        vm.showCreateDialog()

        vm.hideCreateDialog()

        assertFalse(vm.uiState.value.showCreateDialog)
    }

    @Test
    fun `createProject - success hides dialog`() = runTest {
        coEvery { repository.createProject(any()) } returns NetworkResult.Success(projectDto())
        val vm = buildViewModel()
        vm.showCreateDialog()

        vm.createProject("New Project", null)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showCreateDialog)
        coVerify { repository.createProject(ProjectCreateRequest(name = "New Project", description = null)) }
    }

    @Test
    fun `createProject - HTTP error shows snackbar message`() = runTest {
        coEvery { repository.createProject(any()) } returns NetworkResult.Error(422, null)
        val vm = buildViewModel()

        vm.createProject("Duplicate", null)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `createProject - network exception shows snackbar message`() = runTest {
        coEvery { repository.createProject(any()) } returns NetworkResult.Exception(RuntimeException("offline"))
        val vm = buildViewModel()

        vm.createProject("Fail", null)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `deleteProject - HTTP error shows snackbar`() = runTest {
        coEvery { repository.deleteProject(any()) } returns NetworkResult.Error(422, null)
        val vm = buildViewModel()

        vm.deleteProject("proj-1")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `clearSnackbar removes message`() = runTest {
        coEvery { repository.createProject(any()) } returns NetworkResult.Error(500, null)
        val vm = buildViewModel()
        vm.createProject("Fail", null)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.snackbarMessage)

        vm.clearSnackbar()

        assertNull(vm.uiState.value.snackbarMessage)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun projectDto() = ProjectDto(
        id = "proj-1",
        name = "New Project",
        description = null,
        taskCodePrefix = "NP_",
        defaultPhaseId = null,
    )
}
