package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkTokens else LightTokens
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (!view.isInEditMode) {
        // Runs after every successful recomposition — handles initial set and theme-toggle.
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }

        // Re-applies on every ON_RESUME. Some OEMs (Samsung One UI in particular)
        // reset isAppearanceLightStatusBars / isAppearanceLightNavigationBars when
        // another window closes and the Activity regains focus. This is the guard.
        DisposableEffect(lifecycleOwner, darkTheme) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val window = (view.context as Activity).window
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        Surface(
            color = colors.bgBase,
            modifier = Modifier.fillMaxSize()
        ) {
            MaterialTheme(content = content)
        }
    }
}

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
