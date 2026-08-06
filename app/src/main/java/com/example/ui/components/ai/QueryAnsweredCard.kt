package com.example.ui.components.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.model.QueryResponse
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors
import kotlin.math.abs

@Composable
fun QueryAnsweredCard(
    response: QueryResponse,
    onDismiss: () -> Unit,
    onViewTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dir = response.direction
    val amount = response.totalAmount
    val hasAmount = amount != 0.0 || response.transactionCount > 0

    val amountColor = when (dir) {
        "OUT" -> MaterialTheme.appColors.expense
        "IN"  -> MaterialTheme.appColors.income
        "NET" -> if (amount >= 0) MaterialTheme.appColors.income else MaterialTheme.appColors.expense
        else  -> MaterialTheme.appColors.textPrimary
    }
    val amountPrefix = when (dir) {
        "OUT" -> "–₹"
        "IN"  -> "+₹"
        "NET" -> if (amount >= 0) "+₹" else "–₹"
        else  -> "₹"
    }
    val amountText = "%,.0f".format(abs(amount))

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Answer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.appColors.textSecondary
                    )
                }
            }

            if (response.headline.isNotBlank()) {
                Text(
                    text = response.headline,
                    style = AppTypography.bodyRegular,
                    color = MaterialTheme.appColors.textSecondary
                )
            }

            if (hasAmount) {
                Text(
                    text = "$amountPrefix$amountText",
                    style = AppTypography.display,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }

            response.timeContext?.let { ctx ->
                Text(
                    text = ctx,
                    style = AppTypography.caption,
                    color = MaterialTheme.appColors.textTertiary
                )
            }

            if (response.transactionCount > 0) {
                Text(
                    text = "Based on ${response.transactionCount} transaction${if (response.transactionCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textTertiary
                )

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onViewTransactions,
                    modifier = Modifier.align(Alignment.Start),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "View Transactions →",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.appColors.primary
                    )
                }
            }
        }
    }
}
