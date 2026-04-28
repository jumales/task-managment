package com.demo.taskmanager.core.network.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted token store backed by [EncryptedSharedPreferences].
 * Uses the Android Keystore via [MasterKey] so tokens are never stored in plaintext.
 *
 * On first launch after a reinstall or backup-restore the Keystore key may be gone while
 * the encrypted SharedPreferences file still exists. In that case decryption throws
 * [javax.crypto.AEADBadTagException]. We delete the corrupted file and recreate it so the
 * app can start; the user will need to log in again, which is the only safe recovery.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = try {
        buildEncryptedPrefs(context)
    } catch (e: Exception) {
        // Keystore key is gone — wipe the unreadable file and start fresh
        context.deleteSharedPreferences(PREFS_FILE)
        buildEncryptedPrefs(context)
    }

    var accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String
        get() = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var idToken: String
        get() = prefs.getString(KEY_ID_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ID_TOKEN, value).apply()

    /** Unix epoch seconds when [accessToken] expires. */
    var expiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    /** Clears all stored tokens; call on logout or failed refresh. */
    fun clear() = prefs.edit().clear().apply()

    /** Returns true when a non-blank access token is present. */
    fun hasAccessToken(): Boolean = accessToken.isNotBlank()

    companion object {
        private const val PREFS_FILE = "secure_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_EXPIRES_AT = "expires_at"

        private fun buildEncryptedPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }
}
