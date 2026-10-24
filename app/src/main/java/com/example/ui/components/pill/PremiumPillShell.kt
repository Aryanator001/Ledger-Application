package com.example.ui.components.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.appColors

object PillDefaults {
    val Height = 76.dp
    val ButtonSize = 58.dp
    val Gap = 16.dp
    val InnerPadding = 10.dp
}

/**
 * Shared "Pearl Edge" pill shell: gradient capsule with a border + inner ring and a
 * soft tinted shadow, hosting a leading circular button, flexible middle content, and
 * a trailing circular button. Used by both the home-screen and overlay quick-add pills.
 */
@Composable
fun PremiumPillShell(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    content: @Composable RowScope.() -> Unit,
    trailing: @Composable () -> Unit,
) {
    val colors = MaterialTheme.appColors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PillDefaults.Height)
            .shadow(elevation = 20.dp, shape = CircleShape, ambientColor = colors.pillShadow, spotColor = colors.pillShadow)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(colors.pillShellStart, colors.pillShellEnd)))
            .border(width = 1.dp, color = colors.pillBorder, shape = CircleShape)
            .border(width = 1.dp, color = colors.pillRing, shape = CircleShape)
            .padding(PillDefaults.InnerPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Box(modifier = Modifier.size(PillDefaults.Gap))
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
            Box(modifier = Modifier.size(PillDefaults.Gap))
            trailing()
        }
    }
}

/**
 * Reusable 58dp gradient circle button with an inner ring, used for the leading `+`
 * and trailing mic/record/send buttons on both quick-add pills.
 */
@Composable
fun PillCircleButton(
    onClick: () -> Unit,
    ring: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.appColors

    Box(
        modifier = modifier
            .size(PillDefaults.ButtonSize)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(colors.pillButtonStart, colors.pillButtonEnd)))
            .border(width = 1.dp, color = ring, shape = CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
