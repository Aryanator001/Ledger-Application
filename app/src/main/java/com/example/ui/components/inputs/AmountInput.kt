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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography

@Composable
fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    currencySymbol: String = "₹",
    label: String = "Amount ($currencySymbol)",
    isCredit: Boolean = true
) {
    val amountColor = if (isCredit) MaterialTheme.appColors.income else MaterialTheme.appColors.expense

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTypography.bodySmall,
            color = MaterialTheme.appColors.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { 
                // Only allow numbers and decimal point
                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*\$"))) {
                    onValueChange(it)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = AppShapes.md,
            textStyle = AppTypography.amountLarge.copy(
                color = amountColor
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.appColors.primary,
                unfocusedBorderColor = MaterialTheme.appColors.borderMedium,
                focusedTextColor = amountColor,
                unfocusedTextColor = amountColor,
                unfocusedContainerColor = MaterialTheme.appColors.bgSurface2,
                focusedContainerColor = MaterialTheme.appColors.bgSurface2
            ),
            leadingIcon = {
                Text(
                    text = currencySymbol,
                    style = AppTypography.amountLarge.copy(color = amountColor)
                )
            }
        )
    }
}
