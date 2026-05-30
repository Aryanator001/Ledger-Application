package com.example.ui.components.buttons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.appColors

@Composable
fun IconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null,
    disabledTint: Color? = null,
    enabled: Boolean = true
) {
    val appliedTint = tint ?: MaterialTheme.appColors.textSecondary
    val appliedDisabledTint = disabledTint ?: MaterialTheme.appColors.textTertiary
    
    MaterialIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp), // Minimum touch target size
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = appliedTint,
            disabledContentColor = appliedDisabledTint
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}
