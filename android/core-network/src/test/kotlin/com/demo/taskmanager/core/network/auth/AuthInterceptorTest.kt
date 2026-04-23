package com.demo.taskmanager.core.network.auth

import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthInterceptorTest {

    private val tokenStore = mockk<TokenStore>(relaxed = true)
    private val interceptor = AuthInterceptor(tokenStore)
    private val server = MockWebServer()

    private val client = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .build()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `attaches Bearer header when token present`() {
        every { tokenStore.accessToken } returns "test-access-token"
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(server.url("/api/test")).build()).execute()

        val recorded = server.takeRequest()
        assertEquals("Bearer test-access-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `omits Authorization header when token blank`() {
        every { tokenStore.accessToken } returns ""
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(server.url("/api/test")).build()).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `skips auth header for NoAuth tagged requests`() {
        every { tokenStore.accessToken } returns "should-not-be-sent"
        server.enqueue(MockResponse().setResponseCode(200))

        val noAuthRequest = Request.Builder()
            .url(server.url("/api/public"))
            .tag(NoAuth::class.java, NoAuth)
            .build()
        client.newCall(noAuthRequest).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }
}
