package com.example.ui.components.inputs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePicker(
    selectedDateMillis: Long,
    onDateSelected: (Long) -> Unit,
    label: String = "Date",
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    
    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val dateString = dateFormatter.format(Date(selectedDateMillis))
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTypography.bodySmall,
            color = MaterialTheme.appColors.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = dateString,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true },
            shape = AppShapes.md,
            textStyle = AppTypography.bodyRegular,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.appColors.textPrimary,
                disabledBorderColor = MaterialTheme.appColors.borderMedium,
                disabledTrailingIconColor = MaterialTheme.appColors.primary,
                disabledContainerColor = MaterialTheme.appColors.bgSurface2
            ),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Select Date"
                )
            }
        )
    }
    
    if (showDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = MaterialTheme.appColors.bgSurface
            ),
            confirmButton = {
                TextButton(onClick = { 
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                    showDialog = false 
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
