package com.demo.taskmanager.data.repo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.demo.taskmanager.data.api.DeviceTokenApi
import com.demo.taskmanager.data.dto.DeviceTokenRequest
import com.demo.taskmanager.data.dto.DeviceTokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceTokenRepositoryTest {

    private val api: DeviceTokenApi = mockk()
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val context: Context = mockk()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var repo: DeviceTokenRepository

    private val stubResponse = DeviceTokenResponse(
        id = "id-1",
        userId = "user-1",
        token = "fcm-token-123",
        platform = "ANDROID",
        createdAt = "2025-01-01T00:00:00Z",
        lastSeenAt = "2025-01-01T00:00:00Z",
    )

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        repo = DeviceTokenRepository(context, api, json)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `register - success persists token in prefs`() = runTest {
        coEvery { api.register(DeviceTokenRequest(token = "fcm-token-123")) } returns stubResponse

        repo.register("fcm-token-123")

        coVerify { api.register(DeviceTokenRequest(token = "fcm-token-123")) }
        coVerify { editor.putString("registered_token", "fcm-token-123") }
        coVerify { editor.apply() }
    }

    @Test
    fun `register - network error does not persist token`() = runTest {
        coEvery { api.register(any()) } throws RuntimeException("network error")

        repo.register("bad-token")

        coVerify(exactly = 0) { editor.putString("registered_token", any()) }
    }

    @Test
    fun `rotate - updates stored token on success`() = runTest {
        coEvery { api.rotate("old-token", DeviceTokenRequest(token = "new-token")) } returns stubResponse.copy(token = "new-token")

        repo.rotate("old-token", "new-token")

        coVerify { api.rotate("old-token", DeviceTokenRequest(token = "new-token")) }
        coVerify { editor.putString("registered_token", "new-token") }
    }

    @Test
    fun `unregister - reads stored token and calls api delete`() = runTest {
        every { prefs.getString("registered_token", null) } returns "stored-token"
        coEvery { api.unregister("stored-token") } returns Unit

        repo.unregister()

        coVerify { api.unregister("stored-token") }
        coVerify { editor.remove("registered_token") }
    }

    @Test
    fun `unregister - no-op when no token stored`() = runTest {
        every { prefs.getString("registered_token", null) } returns null

        repo.unregister()

        coVerify(exactly = 0) { api.unregister(any()) }
    }

    @Test
    fun `storePendingToken and getPendingToken round-trip`() {
        val tokenSlot = slot<String>()
        every { editor.putString("pending_token", capture(tokenSlot)) } returns editor
        every { prefs.getString("pending_token", null) } answers { tokenSlot.captured }

        repo.storePendingToken("pending-fcm")

        assertEquals("pending-fcm", repo.getPendingToken())
        coVerify { editor.putString("pending_token", "pending-fcm") }
    }
}
