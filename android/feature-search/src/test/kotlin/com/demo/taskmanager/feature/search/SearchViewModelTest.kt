package com.demo.taskmanager.feature.search

import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.TaskSearchHitDto
import com.demo.taskmanager.data.dto.UserSearchHitDto
import com.demo.taskmanager.data.repo.SearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var searchRepository: SearchRepository
    private lateinit var recentQueriesStore: RecentQueriesStore

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        searchRepository = mockk()
        recentQueriesStore = mockk(relaxed = true)

        coEvery { searchRepository.searchTasks(any()) } returns NetworkResult.Success(emptyList())
        coEvery { searchRepository.searchUsers(any()) } returns NetworkResult.Success(emptyList())
        every { recentQueriesStore.queries } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `debounce collapses rapid typing into one API call`() = runTest(dispatcher) {
        val viewModel = SearchViewModel(searchRepository, recentQueriesStore)

        // Emit 5 chars in rapid succession — all within the 300 ms debounce window
        listOf("h", "he", "hel", "hell", "hello").forEach { q ->
            viewModel.onQueryChange(q)
        }

        // Advance past debounce window
        advanceTimeBy(400L)
        advanceUntilIdle()

        // Only the final value should trigger a search
        coVerify(exactly = 1) { searchRepository.searchTasks("hello") }
        coVerify(exactly = 1) { searchRepository.searchUsers("hello") }
        // Intermediate values must not have triggered calls
        coVerify(exactly = 0) { searchRepository.searchTasks(not("hello")) }
    }

    @Test
    fun `results are cleared immediately when query is blanked`() = runTest(dispatcher) {
        val taskHit = TaskSearchHitDto(
            id = "t1", title = "Foo", description = null,
            status = null, projectId = null, projectName = null,
            phaseId = null, phaseName = null, assignedUserId = null, assignedUserName = null,
        )
        coEvery { searchRepository.searchTasks("foo") } returns NetworkResult.Success(listOf(taskHit))

        val viewModel = SearchViewModel(searchRepository, recentQueriesStore)
        viewModel.onQueryChange("foo")
        advanceTimeBy(400L)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.tasks.size)

        // Clear the query
        viewModel.onQueryChange("")
        assertEquals(0, viewModel.uiState.value.tasks.size)
        assertTrue(viewModel.uiState.value.query.isEmpty())
    }

    @Test
    fun `tab change updates active tab without triggering a new search`() = runTest(dispatcher) {
        val viewModel = SearchViewModel(searchRepository, recentQueriesStore)
        assertEquals(SearchTab.TASKS, viewModel.uiState.value.activeTab)

        viewModel.onTabChange(SearchTab.USERS)
        assertEquals(SearchTab.USERS, viewModel.uiState.value.activeTab)

        // No search triggered for a tab-only change
        coVerify(exactly = 0) { searchRepository.searchTasks(any()) }
    }

    @Test
    fun `recent query click populates query field`() = runTest(dispatcher) {
        val viewModel = SearchViewModel(searchRepository, recentQueriesStore)
        viewModel.onRecentQueryClick("sprint-2")
        assertEquals("sprint-2", viewModel.uiState.value.query)
    }
}
