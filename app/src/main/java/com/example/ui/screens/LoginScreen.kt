package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.ui.components.buttons.SecondaryButton
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors
import com.example.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    val appColors = MaterialTheme.appColors
    val authError by authViewModel.authError.collectAsState()
    val webClientId = stringResource(R.string.default_web_client_id)

    // One-shot entrance animations — three staggered fade groups
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }

    val alpha1 by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 0, easing = FastOutSlowInEasing),
        label = "alpha1"
    )
    val alpha2 by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "alpha2"
    )
    val alpha3 by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "alpha3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgBase)
    ) {
        // Subtle gradient: primary tint fades to transparent over the top 300dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            appColors.primary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Scrollable main column — centered when content fits, scrollable when it doesn't
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Group 1: Brand mark + tagline ──────────────────────────
            Column(
                modifier = Modifier.alpha(alpha1),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = appColors.textPrimary)) { append("Khaata") }
                        withStyle(SpanStyle(color = appColors.primary)) { append(".ai") }
                    },
                    style = AppTypography.display,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                Text(
                    text = "AI-powered finance, simplified",
                    style = AppTypography.bodyRegular,
                    color = appColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxxl))

            // ── Group 2: Sign-in buttons ───────────────────────────────
            Column(
                modifier = Modifier.alpha(alpha2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                // Google-compliant button: surface background + multicolor G icon
                OutlinedButton(
                    onClick = {
                        if (!isLoading) {
                            isLoading = true
                            authViewModel.signInWithGoogle(
                                context,
                                webClientId
                            ) { success ->
                                isLoading = false
                                if (success) onLoginSuccess()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppSpacing.buttonHeight),
                    shape = AppShapes.md,
                    border = BorderStroke(1.dp, appColors.borderMedium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = appColors.bgSurface,
                        contentColor = appColors.textPrimary
                    ),
                    enabled = !isLoading
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_g),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.Unspecified  // preserve multicolor SVG
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    Text(
                        text = if (isLoading) "Signing in…" else "Sign in with Google",
                        style = AppTypography.bodyLarge
                    )
                }

                if (BuildConfig.DEBUG) {
                    SecondaryButton(
                        text = "Mock Dev Login",
                        onClick = {
                            if (!isLoading) {
                                isLoading = true
                                authViewModel.clearAuthError()
                                authViewModel.signInWithMockDevUser { success ->
                                    isLoading = false
                                    if (success) onLoginSuccess()
                                }
                            }
                        },
                        isLoading = isLoading,
                        enabled = !isLoading
                    )
                }

                if (authError != null) {
                    Text(
                        text = authError.orEmpty(),
                        style = AppTypography.bodySmall,
                        color = appColors.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxxl))

            // ── Group 3: Terms footer ──────────────────────────────────
            // Uses LinkAnnotation.Clickable (replaces deprecated ClickableText)
            val termsAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(color = appColors.textTertiary)) {
                    append("By continuing you agree to our ")
                }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "TERMS",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = appColors.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://khaata.ai/terms"))
                            )
                        }
                    )
                ) { append("Terms of Service") }
                withStyle(SpanStyle(color = appColors.textTertiary)) { append(" and ") }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "PRIVACY",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = appColors.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://khaata.ai/privacy"))
                            )
                        }
                    )
                ) { append("Privacy Policy") }
            }

            Text(
                text = termsAnnotated,
                style = AppTypography.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(alpha3)
                    .padding(bottom = AppSpacing.xl)
            )
        }
    }
}
