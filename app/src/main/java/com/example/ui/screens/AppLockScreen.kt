package com.example.ui.screens

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.appColors
import com.example.ui.theme.AppTypography
import com.example.ui.security.SecurityViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppLockScreen(
    viewModel: SecurityViewModel,
    onUnlocked: () -> Unit
) {
    val lockMethod by viewModel.lockMethod.collectAsState()
    val isBiometricsEnabled by viewModel.biometricsEnabled.collectAsState()
    val isPinEnabled by viewModel.hasPin.collectAsState()

    val context = LocalContext.current
    var showPin by remember { mutableStateOf(!isBiometricsEnabled && isPinEnabled) }
    var currentPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableStateOf(0) }
    var cooldownRemaining by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    fun triggerError() {
        isError = true
        failedAttempts++
        coroutineScope.launch {
            shakeOffset.animateTo(10f, animationSpec = tween(50))
            shakeOffset.animateTo(-10f, animationSpec = tween(50))
            shakeOffset.animateTo(10f, animationSpec = tween(50))
            shakeOffset.animateTo(-10f, animationSpec = tween(50))
            shakeOffset.animateTo(0f, animationSpec = tween(50))
            delay(300)
            currentPin = ""
            isError = false

            if (failedAttempts >= 5) {
                cooldownRemaining = 30
                while (cooldownRemaining > 0) {
                    delay(1000)
                    cooldownRemaining--
                }
                failedAttempts = 0
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isBiometricsEnabled && context is FragmentActivity) {
            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(context as FragmentActivity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (isPinEnabled) {
                            showPin = true
                        }
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onUnlocked()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Ledger")
                .setSubtitle("Confirm your identity to proceed")
                .setNegativeButtonText(if (isPinEnabled) "Use PIN instead" else "Cancel")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.bgBase)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Icon(
            Icons.Default.Lock, 
            contentDescription = "Lock", 
            modifier = Modifier.size(64.dp), 
            tint = MaterialTheme.appColors.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Ledger Locked", 
            style = AppTypography.display.copy(fontSize = 28.sp),
            color = MaterialTheme.appColors.textPrimary
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (cooldownRemaining > 0) {
            Text(
                "Too many attempts. Try again in ${cooldownRemaining}s", 
                style = AppTypography.bodyRegular,
                color = MaterialTheme.appColors.error
            )
            Spacer(modifier = Modifier.weight(1f))
            return@Column
        }

        if (showPin) {
            Text(
                "Enter your PIN", 
                style = AppTypography.bodyRegular,
                color = if (isError) MaterialTheme.appColors.error else MaterialTheme.appColors.textSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 6 Dots
            Row(
                modifier = Modifier.offset(x = shakeOffset.value.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (i in 0 until 6) {
                    val isFilled = i < currentPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isError) MaterialTheme.appColors.error 
                                else if (isFilled) MaterialTheme.appColors.primary 
                                else MaterialTheme.appColors.borderMedium
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Numpad(
                onNumberClick = { num ->
                    if (currentPin.length < 6 && !isError) {
                        currentPin += num
                        if (currentPin.length == 6) {
                            coroutineScope.launch {
                                if (viewModel.verifyPin(currentPin)) {
                                    onUnlocked()
                                } else {
                                    triggerError()
                                }
                            }
                        }
                    }
                },
                onBackspaceClick = {
                    if (currentPin.isNotEmpty() && !isError) {
                        currentPin = currentPin.dropLast(1)
                    }
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        } else {
            Spacer(modifier = Modifier.weight(1f))
            if (isPinEnabled) {
                TextButton(onClick = { showPin = true }) {
                    Text("Use PIN instead", color = MaterialTheme.appColors.primary)
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
