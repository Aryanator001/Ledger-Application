package com.example.ui.components.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.appColors

/**
 * Shown when the user queries but the database has zero transactions.
 * Onboarding-aware: guides toward recording the first transaction
 * instead of showing a sterile "No data found" message.
 */
@Composable
fun QueryEmptyStateCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.appColors.bgSurface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "No transactions yet!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.appColors.textPrimary
            )

            Text(
                text = "Tap the mic and try saying:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary
            )

            SuggestionChip("Vijay ko 500 rupay diye")
            SuggestionChip("Coffee 100 rupay cash")
            SuggestionChip("Rent 15000 paid")

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismiss) {
                Text("Got it", color = MaterialTheme.appColors.primary)
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.appColors.bgSurface2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "\u201C$text\u201D",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.textPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
