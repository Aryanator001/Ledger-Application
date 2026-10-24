package com.example.ai.overlay

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.theme.DarkTokens
import com.example.ui.theme.LightTokens
import com.example.ui.theme.LocalAppColors

/**
 * MyApplicationTheme casts its LocalView's context to Activity (for status-bar insets), which
 * crashes when the ComposeView's host is a Service. This provides the same AppColors/MaterialTheme
 * wiring without that Activity-only behavior.
 */
@Composable
fun QuickAddOverlayTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkTokens else LightTokens
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(content = content)
    }
}
