package com.demo.taskmanager.core.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val AppLightColors = lightColorScheme(
    primary = Blue800,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Color(0xFF545F71),
    onSecondary = Color.White,
    background = Color(0xFFFDFCFF),
    surface = Color(0xFFFDFCFF),
)

private val AppDarkColors = darkColorScheme(
    primary = Blue100,
    onPrimary = Blue900,
    primaryContainer = Blue800,
    onPrimaryContainer = Blue50,
)

/** App-wide Material 3 theme. Uses dynamic color on Android 12+, static blue palette otherwise. */
@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AppDarkColors
        else -> AppLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
