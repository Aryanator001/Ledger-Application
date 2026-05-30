package com.example.ui.security

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.example.data.security.BiometricAuthenticator
import com.example.data.security.BiometricStatus
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    viewModel: SecurityViewModel,
    activity: FragmentActivity,
    onUnlock: () -> Unit
) {
    val lockMethod by viewModel.lockMethod.collectAsState()
    val biometricEnabled by viewModel.biometricsEnabled.collectAsState()

    var authScreen by remember(lockMethod) { 
        mutableStateOf(if (lockMethod == "PIN") AuthScreenType.PIN_ENTRY else AuthScreenType.SELECTION) 
    }
    var pinInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // FULLSCREEN TOUCH-BLOCKING OVERLAY
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Block touches propagation below the overlay */ }
            .focusable()
            .semantics {
                contentDescription = "App locked"
            }
    ) {
        when (authScreen) {
            AuthScreenType.SELECTION -> {
                AuthSelectionScreen(
                    lockMethod = lockMethod,
                    biometricEnabled = biometricEnabled,
                    activity = activity,
                    onBiometricTap = {
                        error = null
                        triggerBiometricNow(
                            activity = activity,
                            onSuccess = onUnlock,
                            onError = { err -> error = err }
                        )
                    },
                    onPinTap = {
                        error = null
                        pinInput = ""
                        authScreen = AuthScreenType.PIN_ENTRY
                    }
                )
            }

            AuthScreenType.PIN_ENTRY -> {
                PinEntryScreen(
                    activity = activity,
                    pinInput = pinInput,
                    error = error,
                    onPinChange = { pinInput = it },
                    onError = { error = it },
                    onSubmit = { enteredPin ->
                        scope.launch {
                            val valid = viewModel.verifyPin(enteredPin)
                            if (valid) {
                                onUnlock()
                            } else {
                                error = "Incorrect PIN"
                                pinInput = ""
                            }
                        }
                    },
                    onBack = {
                        authScreen = AuthScreenType.SELECTION
                        pinInput = ""
                        error = null
                    }
                )
            }
        }
    }

    BackHandler(enabled = true) { /* Block back */ }
}

enum class AuthScreenType {
    SELECTION, PIN_ENTRY
}

// SELECTION SCREEN (Biometric or PIN choice)
@Composable
private fun AuthSelectionScreen(
    lockMethod: String,
    biometricEnabled: Boolean,
    activity: FragmentActivity,
    onBiometricTap: () -> Unit,
    onPinTap: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ledger Locked",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Authenticate to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        // BIOMETRIC BUTTON
        if (biometricEnabled && lockMethod == "BIOMETRICS") {
            Button(
                onClick = onBiometricTap,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock with Biometrics")
            }
            Spacer(Modifier.height(16.dp))
        }

        // PIN BUTTON (always available as fallback)
        Button(
            onClick = onPinTap,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(Icons.Default.Lock, null)
            Spacer(Modifier.width(8.dp))
            Text("Unlock with PIN")
        }
    }
}

// PIN ENTRY SCREEN (Secure Native EditText layout)
@Composable
private fun PinEntryScreen(
    activity: FragmentActivity,
    pinInput: String,
    error: String?,
    onPinChange: (String) -> Unit,
    onError: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit
) {
    val textColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Enter PIN",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(32.dp))

        // NATIVE EDIT TEXT WRAPPER WITH MODERN BOX STYLING
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(60.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                .border(2.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    EditText(ctx).apply {
                        // Borderless view
                        background = null
                        // Force numeric password keyboard
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        transformationMethod = PasswordTransformationMethod.getInstance()
                        gravity = Gravity.CENTER
                        textSize = 28f // Large display text
                        letterSpacing = 0.5f // Large secure spacing dots
                        filters = arrayOf(InputFilter.LengthFilter(6)) // Lock to 6 characters max
                        
                        setTextColor(textColorArgb)
                        imeOptions = EditorInfo.IME_ACTION_DONE
                        
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_DONE) {
                                val textStr = text.toString()
                                if (textStr.isNotEmpty()) {
                                    onSubmit(textStr)
                                }
                                true
                            } else {
                                false
                            }
                        }
                        
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                val currentText = s?.toString() ?: ""
                                onPinChange(currentText)
                                if (currentText.length == 6) {
                                    onSubmit(currentText)
                                }
                            }
                            override fun afterTextChanged(s: Editable?) {}
                        })
                        
                        // Auto-show local keyboard immediately
                        post {
                            requestFocus()
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                },
                update = { editText ->
                    if (editText.text.toString() != pinInput) {
                        editText.setText(pinInput)
                        editText.setSelection(pinInput.length)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(32.dp))

        // PRIMARY ACTION SUBMIT BUTTON
        Button(
            onClick = { onSubmit(pinInput) },
            enabled = pinInput.length >= 4,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp)
        ) {
            Text("Unlock")
        }

        Spacer(Modifier.height(16.dp))

        // BACK TO SELECTION SCREEN BUTTON
        TextButton(onClick = onBack) {
            Text("Back")
        }
    }
}

// TRIGGER BIOMETRIC ON DEMAND
private fun triggerBiometricNow(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val auth = BiometricAuthenticator(activity)
    val status = auth.canAuthenticate()

    if (status != BiometricStatus.AVAILABLE) {
        onError(
            when (status) {
                BiometricStatus.NOT_ENROLLED -> "No biometrics. Use PIN."
                BiometricStatus.NO_HARDWARE -> "No biometric hardware."
                BiometricStatus.HW_UNAVAILABLE -> "Biometrics unavailable."
                else -> "Cannot authenticate."
            }
        )
        return
    }

    auth.authenticate(
        title = "Unlock Ledger",
        subtitle = "Use biometrics",
        onSuccess = onSuccess,
        onError = onError
    )
}
