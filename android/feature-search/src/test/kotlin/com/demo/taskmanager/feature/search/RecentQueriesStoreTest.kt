package com.demo.taskmanager.feature.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RecentQueriesStoreTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var tempFile: File
    private lateinit var store: RecentQueriesStore

    @BeforeEach
    fun setUp() {
        tempFile = File.createTempFile("search_prefs_test", ".preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFile },
        )
        store = RecentQueriesStore(dataStore)
    }

    @AfterEach
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun `stores up to 10 unique queries with FIFO eviction`() = testScope.runTest {
        // Add 11 queries
        repeat(11) { i -> store.addQuery("query$i") }

        val queries = store.queries.first()

        assertEquals(RecentQueriesStore.MAX_QUERIES, queries.size)
        // Most recent at front
        assertEquals("query10", queries.first())
        // Oldest (query0) evicted
        assertFalse("query0" in queries)
    }

    @Test
    fun `duplicate query moves to front without increasing size`() = testScope.runTest {
        store.addQuery("alpha")
        store.addQuery("beta")
        store.addQuery("gamma")
        // Re-add oldest query — should move to front
        store.addQuery("alpha")

        val queries = store.queries.first()

        assertEquals(3, queries.size)
        assertEquals("alpha", queries[0])
        assertEquals("gamma", queries[1])
        assertEquals("beta", queries[2])
    }

    @Test
    fun `blank and whitespace-only queries are ignored`() = testScope.runTest {
        store.addQuery("")
        store.addQuery("  ")
        store.addQuery("\t")

        val queries = store.queries.first()
        assertTrue(queries.isEmpty())
    }

    @Test
    fun `queries survive across store instances (persistence check)`() = testScope.runTest {
        store.addQuery("persistent")

        // Create a second store backed by the same file
        val dataStore2 = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFile },
        )
        val store2 = RecentQueriesStore(dataStore2)

        val queries = store2.queries.first()
        assertTrue("persistent" in queries)
    }
}
