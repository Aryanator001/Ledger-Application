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
import com.example.ui.theme.appColors
import com.example.ui.theme.AppTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReminderSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Monthly") }
    var day by remember { mutableStateOf("1st") }
    var time by remember { mutableStateOf("9:00 AM") }
    var account by remember { mutableStateOf("Cash") }

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
                text = "New Reminder",
                style = AppTypography.display.copy(fontSize = 24.sp),
                color = MaterialTheme.appColors.textPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (e.g. Electricity bill)", color = MaterialTheme.appColors.textSecondary) },
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
                label = { Text("Amount (optional)", color = MaterialTheme.appColors.textSecondary) },
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
                    label = { Text("Frequency", color = MaterialTheme.appColors.textSecondary) },
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
                    label = { Text("Day", color = MaterialTheme.appColors.textSecondary) },
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
                    label = { Text("Time", color = MaterialTheme.appColors.textSecondary) },
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
                    label = { Text("Account (optional)", color = MaterialTheme.appColors.textSecondary) },
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
                Text("Save Reminder", color = Color.White, style = AppTypography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
