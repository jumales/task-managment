package com.demo.taskmanager.core.network.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TokenStore] using MockK static interception to bypass
 * [EncryptedSharedPreferences] Android Keystore dependencies.
 */
class TokenStoreTest {

    private val mockPrefs: SharedPreferences = mockk(relaxed = true)
    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private lateinit var store: TokenStore

    @BeforeEach
    fun setUp() {
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.clear() } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor

        // Intercept the MasterKey.Builder constructor and EncryptedSharedPreferences static factory
        // before the TokenStore init block fires, avoiding real Android Keystore access.
        mockkConstructor(MasterKey.Builder::class, relaxed = true)
        mockkStatic(EncryptedSharedPreferences::class)
        every {
            EncryptedSharedPreferences.create(any(), any(), any<MasterKey>(), any(), any())
        } returns mockPrefs

        store = TokenStore(mockk<Context>())
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── accessToken ────────────────────────────────────────────────────────────

    @Test
    fun `accessToken write delegates to prefs editor`() {
        store.accessToken = "new-token"

        verify { mockEditor.putString("access_token", "new-token") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `accessToken read returns stored value`() {
        every { mockPrefs.getString("access_token", "") } returns "stored-token"

        assertEquals("stored-token", store.accessToken)
    }

    @Test
    fun `accessToken read falls back to empty string when null`() {
        every { mockPrefs.getString("access_token", "") } returns null

        assertEquals("", store.accessToken)
    }

    // ── refreshToken / idToken ─────────────────────────────────────────────────

    @Test
    fun `refreshToken round-trip delegates to prefs`() {
        store.refreshToken = "rt-value"

        verify { mockEditor.putString("refresh_token", "rt-value") }
    }

    @Test
    fun `idToken round-trip delegates to prefs`() {
        store.idToken = "id-value"

        verify { mockEditor.putString("id_token", "id-value") }
    }

    // ── expiresAt ──────────────────────────────────────────────────────────────

    @Test
    fun `expiresAt write uses putLong`() {
        store.expiresAt = 9999L

        verify { mockEditor.putLong("expires_at", 9999L) }
    }

    @Test
    fun `expiresAt read falls back to 0 when absent`() {
        every { mockPrefs.getLong("expires_at", 0L) } returns 0L

        assertEquals(0L, store.expiresAt)
    }

    // ── clear ──────────────────────────────────────────────────────────────────

    @Test
    fun `clear removes all prefs entries and applies`() {
        store.clear()

        verify { mockEditor.clear() }
        verify { mockEditor.apply() }
    }

    // ── hasAccessToken ─────────────────────────────────────────────────────────

    @Test
    fun `hasAccessToken returns true when token is non-blank`() {
        every { mockPrefs.getString("access_token", "") } returns "valid-token"

        assertTrue(store.hasAccessToken())
    }

    @Test
    fun `hasAccessToken returns false when token is blank`() {
        every { mockPrefs.getString("access_token", "") } returns ""

        assertFalse(store.hasAccessToken())
    }

    @Test
    fun `hasAccessToken returns false when token is null`() {
        every { mockPrefs.getString("access_token", "") } returns null

        assertFalse(store.hasAccessToken())
    }
}
