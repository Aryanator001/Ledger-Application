package com.example.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import com.example.R
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography

@Composable
fun SummaryCard(
    totalBalance: Double,
    totalIncome: Double,
    totalExpense: Double,
    modifier: Modifier = Modifier,
    currencySymbol: String = "₹"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.xl)
            .background(MaterialTheme.appColors.primary)
            .padding(24.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.summary_total_balance),
                style = AppTypography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$currencySymbol${String.format("%.2f", totalBalance)}",
                style = AppTypography.amountLarge,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(
                    label = stringResource(R.string.income),
                    amount = totalIncome,
                    icon = Icons.Default.KeyboardArrowDown,
                    iconTint = MaterialTheme.appColors.income,
                    currencySymbol = currencySymbol,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                SummaryItem(
                    label = stringResource(R.string.expense),
                    amount = totalExpense,
                    icon = Icons.Default.KeyboardArrowUp,
                    iconTint = MaterialTheme.appColors.expense,
                    currencySymbol = currencySymbol,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    amount: Double,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    currencySymbol: String = "₹"
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = label,
                style = AppTypography.caption,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = "$currencySymbol${String.format("%.0f", amount)}",
                style = AppTypography.amount,
                color = Color.White
            )
        }
    }
}
