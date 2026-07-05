package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.theme.appColors
import com.example.ui.theme.AppTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReminderSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val defaultFrequency = stringResource(R.string.create_reminder_default_frequency)
    val defaultDay = stringResource(R.string.create_reminder_default_day)
    val defaultTime = stringResource(R.string.create_reminder_default_time)
    val cashLabel = stringResource(R.string.account_cash)

    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var frequency by remember(defaultFrequency) { mutableStateOf(defaultFrequency) }
    var day by remember(defaultDay) { mutableStateOf(defaultDay) }
    var time by remember(defaultTime) { mutableStateOf(defaultTime) }
    var account by remember(cashLabel) { mutableStateOf(cashLabel) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.appColors.bgSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.create_reminder_title),
                style = AppTypography.display.copy(fontSize = 24.sp),
                color = MaterialTheme.appColors.textPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.create_reminder_label_field), color = MaterialTheme.appColors.textSecondary) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textStyle = AppTypography.bodyRegular.copy(color = MaterialTheme.appColors.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.appColors.primary,
                    unfocusedBorderColor = MaterialTheme.appColors.borderLight
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.create_reminder_amount_field), color = MaterialTheme.appColors.textSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textStyle = AppTypography.bodyRegular.copy(color = MaterialTheme.appColors.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.appColors.primary,
                    unfocusedBorderColor = MaterialTheme.appColors.borderLight
                ),
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = frequency,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.create_reminder_frequency_field), color = MaterialTheme.appColors.textSecondary) },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    textStyle = AppTypography.bodyRegular.copy(color = MaterialTheme.appColors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.appColors.borderLight,
                        unfocusedBorderColor = MaterialTheme.appColors.borderLight
                    ),
                    singleLine = true
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.create_reminder_day_field), color = MaterialTheme.appColors.textSecondary) },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    textStyle = AppTypography.bodyRegular.copy(color = MaterialTheme.appColors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.appColors.borderLight,
                        unfocusedBorderColor = MaterialTheme.appColors.borderLight
                    ),
                    singleLine = true
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = time,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.create_reminder_time_field), color = MaterialTheme.appColors.textSecondary) },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    textStyle = AppTypography.bodyRegular.copy(color = MaterialTheme.appColors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.appColors.borderLight,
                        unfocusedBorderColor = MaterialTheme.appColors.borderLight
                    ),
                    singleLine = true
                )
                OutlinedTextField(
                    value = account,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.create_reminder_account_field), color = MaterialTheme.appColors.textSecondary) },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    textStyle = AppTypography.bodyRegular.copy(color = MaterialTheme.appColors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.appColors.borderLight,
                        unfocusedBorderColor = MaterialTheme.appColors.borderLight
                    ),
                    singleLine = true
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary)
            ) {
                Text(stringResource(R.string.create_reminder_save_btn), color = Color.White, style = AppTypography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
