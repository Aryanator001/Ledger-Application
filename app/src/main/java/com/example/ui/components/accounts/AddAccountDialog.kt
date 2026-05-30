package com.example.ui.components.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Bank") }
    var selectedColor by remember { mutableStateOf("#5B8DEF") }

    val accountTypes = listOf("Bank", "Cash", "UPI", "Wallet", "Credit Card", "Online")
    val colors = listOf("#5B8DEF", "#6FAF8F", "#D9A441", "#8B7FD1", "#5E9CA0", "#C27A7A", "#4B556B")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = AppShapes.lg,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Account",
                        style = AppTypography.headline3,
                        color = MaterialTheme.appColors.textPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.appColors.textSecondary)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name", color = MaterialTheme.appColors.textSecondary) },
                    placeholder = { Text("e.g. HDFC Savings", color = MaterialTheme.appColors.textTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = AppShapes.md,
                    textStyle = AppTypography.bodyRegular.copy(color = MaterialTheme.appColors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.appColors.primary,
                        unfocusedBorderColor = MaterialTheme.appColors.borderMedium,
                        unfocusedContainerColor = MaterialTheme.appColors.bgSurface2,
                        focusedContainerColor = MaterialTheme.appColors.bgSurface2
                    )
                )

                Text("Account Type", style = AppTypography.bodySmall, color = MaterialTheme.appColors.textSecondary)
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(accountTypes) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.appColors.bgSurface2,
                                labelColor = MaterialTheme.appColors.textSecondary,
                                selectedContainerColor = MaterialTheme.appColors.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.appColors.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = MaterialTheme.appColors.borderMedium,
                                enabled = true,
                                selected = selectedType == type
                            )
                        )
                    }
                }

                Text("Theme Color", style = AppTypography.bodySmall, color = MaterialTheme.appColors.textSecondary)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colors.forEach { colorHex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(colorHex)))
                                .clickable { selectedColor = colorHex }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == colorHex) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onConfirm(name, selectedType, selectedColor)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.md,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary)
                ) {
                    Text("Create Account", style = AppTypography.bodyRegular, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
