package com.example.ui.components.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.theme.appColors
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String? = null,
    dismissText: String? = null,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val resolvedConfirmText = confirmText ?: stringResource(R.string.action_confirm)
    val resolvedDismissText = dismissText ?: stringResource(R.string.action_cancel)
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
                    text = resolvedConfirmText,
                    style = AppTypography.bodyLarge,
                    color = if (isDestructive) MaterialTheme.appColors.error else MaterialTheme.appColors.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = resolvedDismissText,
                    style = AppTypography.bodyLarge,
                    color = MaterialTheme.appColors.textSecondary
                )
            }
        },
        containerColor = MaterialTheme.appColors.bgSurface,
        shape = AppShapes.lg
    )
}
