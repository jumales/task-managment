package com.demo.taskmanager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test — verifies the app launches without crashing.
 * Run with: ./gradlew :app:pixel6api33DebugAndroidTest (Gradle Managed Device)
 * or         ./gradlew :app:connectedEmulatorDebugAndroidTest (connected device)
 *
 * Full login/navigation tests require a pre-seeded TokenStore or a Hilt fake
 * AuthManager module — see plans/android/task_18_tests.md for the roadmap.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_root_composable_without_crash() {
        composeRule.onRoot().assertIsDisplayed()
    }
}
