package com.demo.taskmanager.feature.projects.detail

import androidx.lifecycle.SavedStateHandle
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.PhaseDto
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.dto.enums.TaskPhaseName
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>(relaxed = true)
    private val authManager = mockk<AuthManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authManager.authState } returns MutableStateFlow(
            AuthState.Authenticated(userId = "user-1", roles = listOf("ADMIN"))
        )
        coEvery { repository.getProject(any()) } returns NetworkResult.Success(projectDto())
        coEvery { repository.getPhases(any()) } returns NetworkResult.Success(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(projectId: String = "proj-1") = ProjectDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("projectId" to projectId)),
        repository = repository,
        authManager = authManager,
    )

    @Test
    fun `loads project and phases in parallel on init`() = runTest {
        val phases = listOf(phaseDto("ph-1"), phaseDto("ph-2"))
        coEvery { repository.getPhases("proj-1") } returns NetworkResult.Success(phases)

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value as ProjectDetailUiState.Loaded
        assertEquals("Test Project", state.project.name)
        assertEquals(2, state.phases.size)
        assertTrue(state.isAdmin)
    }

    @Test
    fun `setDefaultPhase calls updateProject with correct defaultPhaseId`() = runTest {
        val phase = phaseDto("ph-1")
        coEvery { repository.getPhases("proj-1") } returns NetworkResult.Success(listOf(phase))
        coEvery { repository.updateProject("proj-1", any()) } returns NetworkResult.Success(
            projectDto().copy(defaultPhaseId = "ph-1")
        )

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setDefaultPhase("ph-1")
        advanceUntilIdle()

        coVerify {
            repository.updateProject("proj-1", match { it.defaultPhaseId == "ph-1" })
        }
        val state = vm.uiState.value as ProjectDetailUiState.Loaded
        assertEquals("ph-1", state.project.defaultPhaseId)
    }

    @Test
    fun `setDefaultPhase passes null to clear the default`() = runTest {
        coEvery { repository.getPhases("proj-1") } returns NetworkResult.Success(listOf(phaseDto("ph-1")))
        coEvery { repository.updateProject("proj-1", any()) } returns NetworkResult.Success(
            projectDto().copy(defaultPhaseId = null)
        )

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setDefaultPhase(null)
        advanceUntilIdle()

        coVerify {
            repository.updateProject("proj-1", match { it.defaultPhaseId == null })
        }
    }

    @Test
    fun `deletePhase removes it from list on success`() = runTest {
        val phase = phaseDto("ph-1")
        coEvery { repository.getPhases("proj-1") } returns NetworkResult.Success(listOf(phase))
        coEvery { repository.deletePhase("ph-1") } returns NetworkResult.Success(Unit)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deletePhase("ph-1")
        advanceUntilIdle()

        val state = vm.uiState.value as ProjectDetailUiState.Loaded
        assertTrue(state.phases.isEmpty())
    }

    @Test
    fun `deletePhase sets snackbar with server error message on 422`() = runTest {
        val phase = phaseDto("ph-1")
        coEvery { repository.getPhases("proj-1") } returns NetworkResult.Success(listOf(phase))
        coEvery { repository.deletePhase("ph-1") } returns NetworkResult.Error(422, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deletePhase("ph-1")
        advanceUntilIdle()

        val state = vm.uiState.value as ProjectDetailUiState.Loaded
        assertNotNull(state.snackbarMessage)
        // Phase not removed — list still contains it
        assertEquals(1, state.phases.size)
    }

    @Test
    fun `updatePhase replaces phase in list on success`() = runTest {
        val phase = phaseDto("ph-1")
        val updated = phase.copy(customName = "My Sprint")
        coEvery { repository.getPhases("proj-1") } returns NetworkResult.Success(listOf(phase))
        coEvery { repository.updatePhase("ph-1", any()) } returns NetworkResult.Success(updated)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.updatePhase("ph-1", "My Sprint")
        advanceUntilIdle()

        val state = vm.uiState.value as ProjectDetailUiState.Loaded
        assertEquals("My Sprint", state.phases.first().customName)
    }

    @Test
    fun `clearSnackbar removes message`() = runTest {
        coEvery { repository.getPhases(any()) } returns NetworkResult.Success(listOf(phaseDto("ph-1")))
        coEvery { repository.deletePhase(any()) } returns NetworkResult.Error(500, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deletePhase("ph-1")
        advanceUntilIdle()
        assertNotNull((vm.uiState.value as? ProjectDetailUiState.Loaded)?.snackbarMessage)

        vm.clearSnackbar()
        assertNull((vm.uiState.value as? ProjectDetailUiState.Loaded)?.snackbarMessage)
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun projectDto(defaultPhaseId: String? = null) = ProjectDto(
        id = "proj-1",
        name = "Test Project",
        description = "A test project",
        taskCodePrefix = "TEST",
        defaultPhaseId = defaultPhaseId,
    )

    private fun phaseDto(id: String) = PhaseDto(
        id = id,
        name = TaskPhaseName.IN_PROGRESS,
        description = null,
        customName = null,
        projectId = "proj-1",
    )
}
