package com.example.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Category
import com.example.data.Transaction
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionCard(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    currencySymbol: String = "₹",
    category: Category? = null
) {
    val isIncome = transaction.type.equals("INCOME", ignoreCase = true) || transaction.type.equals("CREDIT", ignoreCase = true)
    val amountColor = if (isIncome) MaterialTheme.appColors.income else MaterialTheme.appColors.textPrimary
    val prefix = if (isIncome) "+" else "-"
    
    val dateFormatter = SimpleDateFormat("MMM dd", Locale.getDefault())
    val dateString = dateFormatter.format(Date(transaction.date))

    val displayTitle = if ((transaction.title.isBlank() || transaction.title.equals("Transaction", ignoreCase = true)) && category != null) {
        category.name
    } else if (transaction.title.isBlank()) {
        "Transaction"
    } else {
        transaction.title
    }
    val showCategoryInSubtitle = category != null && displayTitle != category.name

    val categoryColor = category?.colorHex?.let {
        try {
            Color(android.graphics.Color.parseColor(it))
        } catch (e: Exception) {
            MaterialTheme.appColors.primary
        }
    } ?: MaterialTheme.appColors.primary

    val categoryBg = category?.colorHex?.let {
        try {
            Color(android.graphics.Color.parseColor(it)).copy(alpha = 0.15f)
        } catch (e: Exception) {
            MaterialTheme.appColors.bgSurface2
        }
    } ?: MaterialTheme.appColors.bgSurface2

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.lg)
            .background(MaterialTheme.appColors.bgSurface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(categoryBg),
                contentAlignment = Alignment.Center
            ) {
                if (category != null && category.icon.isNotBlank()) {
                    val isEmoji = category.icon.any { it.isSurrogate() || it.code > 127 }
                    if (isEmoji) {
                        Text(
                            text = category.icon,
                            fontSize = 20.sp
                        )
                    } else {
                        val mappedEmoji = when (category.icon.lowercase()) {
                            "restaurant" -> "🍔"
                            "receipt_long" -> "🧾"
                            "shopping_bag" -> "🛍️"
                            "more_horiz" -> "⚙️"
                            else -> category.icon.take(1)
                        }
                        Text(
                            text = mappedEmoji,
                            fontSize = 20.sp
                        )
                    }
                } else {
                    Text(
                        text = displayTitle.take(1).uppercase(),
                        color = MaterialTheme.appColors.primary,
                        style = AppTypography.headline3
                    )
                }
            }
            
            Column {
                Text(
                    text = displayTitle,
                    style = AppTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.appColors.textPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showCategoryInSubtitle && category != null) {
                        Text(
                            text = category.name,
                            style = AppTypography.caption.copy(fontWeight = FontWeight.Bold, color = categoryColor)
                        )
                        Text(
                            text = " • ",
                            style = AppTypography.caption,
                            color = MaterialTheme.appColors.textSecondary
                        )
                    }
                    Text(
                        text = dateString,
                        style = AppTypography.caption,
                        color = MaterialTheme.appColors.textSecondary
                    )
                    if (!transaction.description.isNullOrBlank()) {
                        Text(
                            text = " • ${transaction.description}",
                            style = AppTypography.caption,
                            color = MaterialTheme.appColors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        
        Text(
            text = "$prefix $currencySymbol${String.format("%.0f", transaction.amount)}",
            style = AppTypography.amount,
            color = amountColor,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
