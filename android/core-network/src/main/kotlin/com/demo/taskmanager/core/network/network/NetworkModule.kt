package com.demo.taskmanager.core.network.network

import android.content.Context
import com.demo.taskmanager.core.network.auth.AppAuthTokenRefresher
import com.demo.taskmanager.core.network.auth.AuthConfig
import com.demo.taskmanager.core.network.auth.AuthEvent
import com.demo.taskmanager.core.network.auth.AuthInterceptor
import com.demo.taskmanager.core.network.auth.TokenRefreshAuthenticator
import com.demo.taskmanager.core.network.auth.TokenRefresher
import com.demo.taskmanager.core.network.auth.TokenStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module that assembles the entire network stack: [OkHttpClient], [Retrofit], and [Json].
 * The app module must provide two named strings — [BASE_URL] and [KEYCLOAK_ISSUER] — so
 * this library module stays decoupled from [BuildConfig].
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Named qualifier for the backend base URL (e.g. "http://10.0.2.2:8080"). */
    const val BASE_URL = "BASE_URL"

    /** Named qualifier for the Keycloak issuer URL including realm path. */
    const val KEYCLOAK_ISSUER = "KEYCLOAK_ISSUER"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideAuthConfig(@Named(KEYCLOAK_ISSUER) issuerUri: String): AuthConfig =
        AuthConfig(issuerUri)

    @Provides
    @Singleton
    fun provideAuthorizationService(@ApplicationContext context: Context): AuthorizationService =
        AuthorizationService(context)

    @Provides
    @Singleton
    fun provideTokenRefresher(impl: AppAuthTokenRefresher): TokenRefresher = impl

    @Provides
    @Singleton
    fun provideAuthEvents(): MutableSharedFlow<AuthEvent> =
        MutableSharedFlow(extraBufferCapacity = 1)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        tokenRefreshAuthenticator: TokenRefreshAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .apply {
            // FLAG_DEBUGGABLE is set by the build system on debug APKs; safe to read in library.
            if (isDebugBuild(context)) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                )
            }
        }
        .addInterceptor(authInterceptor)
        .authenticator(tokenRefreshAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        @Named(BASE_URL) baseUrl: String,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /** Returns true when the APK was built with debuggable=true. */
    private fun isDebugBuild(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
