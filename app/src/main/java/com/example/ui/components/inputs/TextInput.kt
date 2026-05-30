package com.example.ui.components.inputs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTypography.bodySmall,
            color = if (isError) MaterialTheme.appColors.error else MaterialTheme.appColors.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                if (placeholder.isNotEmpty()) {
                    Text(placeholder, style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textTertiary)
                }
            },
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            shape = AppShapes.md,
            textStyle = AppTypography.bodyRegular,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.appColors.primary,
                unfocusedBorderColor = MaterialTheme.appColors.borderMedium,
                errorBorderColor = MaterialTheme.appColors.error,
                focusedTextColor = MaterialTheme.appColors.textPrimary,
                unfocusedTextColor = MaterialTheme.appColors.textPrimary,
                unfocusedContainerColor = MaterialTheme.appColors.bgSurface2,
                focusedContainerColor = MaterialTheme.appColors.bgSurface2
            )
        )
        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = AppTypography.caption,
                color = MaterialTheme.appColors.error
            )
        }
    }
}
