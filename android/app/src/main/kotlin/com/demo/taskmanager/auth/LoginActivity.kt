package com.demo.taskmanager.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.demo.taskmanager.MainActivity
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Entry-point activity for the PKCE login flow.
 * Presents a single "Log in" button that delegates to [AuthManager.buildLoginIntent].
 * On successful callback, forwards to [MainActivity] and finishes itself.
 */
@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { authManager.handleCallback(it) }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        authManager.buildLoginIntent { intent -> authLauncher.launch(intent) }
                    }) {
                        Text("Log in")
                    }
                }
            }
        }
    }
}
