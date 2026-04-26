package com.demo.taskmanager.feature.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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

    private lateinit var tempFile: File

    @BeforeEach
    fun setUp() {
        tempFile = File.createTempFile("search_prefs_test", ".preferences_pb")
    }

    @AfterEach
    fun tearDown() {
        tempFile.delete()
    }

    // DataStore is created inside each runTest using backgroundScope so its internal
    // file-writer coroutines don't cause UncompletedCoroutinesError at test teardown.
    private fun buildStore(
        scope: CoroutineScope,
        file: File = tempFile,
    ): RecentQueriesStore {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return RecentQueriesStore(dataStore)
    }

    @Test
    fun `stores up to 10 unique queries with FIFO eviction`() = runTest {
        val store = buildStore(backgroundScope)
        repeat(11) { i -> store.addQuery("query$i") }

        val queries = store.queries.first()

        assertEquals(RecentQueriesStore.MAX_QUERIES, queries.size)
        assertEquals("query10", queries.first())
        assertFalse("query0" in queries)
    }

    @Test
    fun `duplicate query moves to front without increasing size`() = runTest {
        val store = buildStore(backgroundScope)
        store.addQuery("alpha")
        store.addQuery("beta")
        store.addQuery("gamma")
        store.addQuery("alpha")

        val queries = store.queries.first()

        assertEquals(3, queries.size)
        assertEquals("alpha", queries[0])
        assertEquals("gamma", queries[1])
        assertEquals("beta", queries[2])
    }

    @Test
    fun `blank and whitespace-only queries are ignored`() = runTest {
        val store = buildStore(backgroundScope)
        store.addQuery("")
        store.addQuery("  ")
        store.addQuery("\t")

        val queries = store.queries.first()
        assertTrue(queries.isEmpty())
    }

    @Test
    fun `queries survive across store instances (persistence check)`() = runTest {
        // Use a dedicated scope for store1 so we can cancel it (release the file lock)
        // before opening the same file with store2 — DataStore enforces exclusive file access.
        val scope1 = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        buildStore(scope1).addQuery("persistent")
        scope1.cancel()

        val store2 = buildStore(backgroundScope)
        assertTrue("persistent" in store2.queries.first())
    }
}
