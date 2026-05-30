package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.appColors
import com.example.ui.theme.AppTypography
import com.example.ui.security.SecurityViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPINScreen(
    viewModel: SecurityViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var firstPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    val title = if (step == 1) "Create your PIN" else "Confirm PIN"
    val subtitle = if (step == 1) "Choose a 6-digit PIN to lock the app" else "Re-enter the 6-digit PIN"

    fun triggerError() {
        isError = true
        coroutineScope.launch {
            shakeOffset.animateTo(10f, animationSpec = tween(50))
            shakeOffset.animateTo(-10f, animationSpec = tween(50))
            shakeOffset.animateTo(10f, animationSpec = tween(50))
            shakeOffset.animateTo(-10f, animationSpec = tween(50))
            shakeOffset.animateTo(0f, animationSpec = tween(50))
            delay(300)
            currentPin = ""
            isError = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.appColors.bgBase,
                    titleContentColor = MaterialTheme.appColors.textPrimary,
                    navigationIconContentColor = MaterialTheme.appColors.textPrimary
                )
            )
        },
        containerColor = MaterialTheme.appColors.bgBase
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                title, 
                style = AppTypography.display.copy(fontSize = 28.sp),
                color = MaterialTheme.appColors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle, 
                style = AppTypography.bodyRegular,
                color = if (isError) MaterialTheme.appColors.error else MaterialTheme.appColors.textSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

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

            // Numpad
            Numpad(
                onNumberClick = { num ->
                    if (currentPin.length < 6 && !isError) {
                        currentPin += num
                        if (currentPin.length == 6) {
                            coroutineScope.launch {
                                delay(200) // small delay for visual feedback
                                if (step == 1) {
                                    if (isObviousPin(currentPin)) {
                                        triggerError()
                                    } else {
                                        firstPin = currentPin
                                        currentPin = ""
                                        step = 2
                                    }
                                } else {
                                    if (currentPin == firstPin) {
                                        // Success
                                        viewModel.savePin(currentPin)
                                        onSuccess()
                                    } else {
                                        triggerError()
                                    }
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
        }
    }
}

fun isObviousPin(pin: String): Boolean {
    val obvious = listOf("000000", "111111", "222222", "333333", "444444", "555555", "666666", "777777", "888888", "999999", "123456", "654321")
    return obvious.contains(pin)
}

@Composable
fun Numpad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "back")
        )

        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (key in row) {
                    if (key == "") {
                        Spacer(modifier = Modifier.size(72.dp))
                    } else if (key == "back") {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.appColors.bgSurface2)
                                .clickable { onBackspaceClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace, 
                                contentDescription = "Backspace",
                                tint = MaterialTheme.appColors.textPrimary
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.appColors.bgSurface)
                                .clickable { onNumberClick(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                style = AppTypography.display.copy(fontSize = 32.sp),
                                color = MaterialTheme.appColors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
