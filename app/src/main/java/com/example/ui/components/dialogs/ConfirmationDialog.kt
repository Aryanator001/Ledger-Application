package com.example.ui.components.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.ui.theme.appColors
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = AppTypography.headline3, color = MaterialTheme.appColors.textPrimary)
        },
        text = {
            Text(text = message, style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textSecondary)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    style = AppTypography.bodyLarge,
                    color = if (isDestructive) MaterialTheme.appColors.error else MaterialTheme.appColors.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissText,
                    style = AppTypography.bodyLarge,
                    color = MaterialTheme.appColors.textSecondary
                )
            }
        },
        containerColor = MaterialTheme.appColors.bgSurface,
        shape = AppShapes.lg
    )
}
