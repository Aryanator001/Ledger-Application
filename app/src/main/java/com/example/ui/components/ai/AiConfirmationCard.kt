package com.example.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.extract.CandidateResolver
import com.example.ai.model.CandidateTransaction
import com.example.ai.model.Direction
import com.example.ai.model.ValidationError
import com.example.data.Account
import com.example.data.Category
import com.example.ui.theme.appColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfirmationCard(
    candidate: CandidateTransaction,
    categories: List<Category>,
    accounts: List<Account>,
    resolver: CandidateResolver,
    errors: List<ValidationError>,
    onConfirm: () -> Unit,
    onOverrideDuplicate: () -> Unit,
    onEdit: (CandidateTransaction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDirection by remember { mutableStateOf(candidate.direction ?: Direction.EXPENSE) }
    var amountText by remember { mutableStateOf(candidate.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var entity by remember { mutableStateOf(candidate.entity ?: "") }
    var selectedCategoryId by remember { mutableStateOf(resolver.resolveCategoryId(candidate.categoryHint)) }
    var selectedAccountId by remember { mutableStateOf(resolver.resolveAccountId(candidate.paymentMethodHint)) }
    var notes by remember { mutableStateOf(candidate.notes ?: "") }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.appColors.bgSurface)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confirm Transaction",
                    color = MaterialTheme.appColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.appColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isDuplicate = errors.any { it is ValidationError.DuplicateSuspected }
            if (isDuplicate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "This looks like a duplicate entry",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val directionError = errors.firstOrNull { it is ValidationError.DirectionMissing }
            val directionRows = listOf(
                listOf(Direction.INCOME, Direction.EXPENSE, Direction.TRANSFER),
                listOf(Direction.CREDIT, Direction.DEBIT)
            )
            fun directionLabel(d: Direction) = when (d) {
                Direction.INCOME -> "Income"
                Direction.EXPENSE -> "Expense"
                Direction.TRANSFER -> "Transfer"
                Direction.CREDIT -> "Credit"
                Direction.DEBIT -> "Debit"
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                directionRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { direction ->
                            FilterChip(
                                selected = selectedDirection == direction,
                                onClick = {
                                    selectedDirection = direction
                                    onEdit(candidate.copy(direction = direction))
                                },
                                label = {
                                    Text(
                                        text = directionLabel(direction),
                                        fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Pad shorter rows so all chips stay 1/3 width
                        repeat(directionRows[0].size - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (directionError != null) {
                Text(
                    text = directionError.userMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.appColors.textPrimary,
                unfocusedTextColor = MaterialTheme.appColors.textPrimary,
                focusedLabelColor = MaterialTheme.appColors.textSecondary,
                unfocusedLabelColor = MaterialTheme.appColors.textSecondary,
                focusedBorderColor = MaterialTheme.appColors.primary,
                unfocusedBorderColor = MaterialTheme.appColors.borderLight,
                focusedLeadingIconColor = MaterialTheme.appColors.textPrimary,
                unfocusedLeadingIconColor = MaterialTheme.appColors.textSecondary,
                focusedTrailingIconColor = MaterialTheme.appColors.textSecondary,
                unfocusedTrailingIconColor = MaterialTheme.appColors.textSecondary
            )

            val amountError = errors.firstOrNull { it is ValidationError.AmountMissing || it is ValidationError.AmountInvalid }
            OutlinedTextField(
                value = amountText,
                onValueChange = { newValue ->
                    amountText = newValue.filter { it.isDigit() || it == '.' }
                    onEdit(candidate.copy(amount = amountText.toDoubleOrNull()))
                },
                label = { Text("Amount") },
                prefix = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.appColors.textPrimary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it.userMessage, color = MaterialTheme.colorScheme.error) } },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(8.dp))

            val entityError = errors.firstOrNull { it is ValidationError.EntityMissing }
            OutlinedTextField(
                value = entity,
                onValueChange = { newValue ->
                    entity = newValue
                    onEdit(candidate.copy(entity = entity.trim().ifEmpty { null }))
                },
                label = { Text("Paid to / Received from") },
                isError = entityError != null,
                supportingText = entityError?.let { { Text(it.userMessage, color = MaterialTheme.colorScheme.error) } },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(8.dp))

            var categoryExpanded by remember { mutableStateOf(false) }
            val selectedCategory = categories.find { it.id == selectedCategoryId }
            val categoryError = errors.firstOrNull { it is ValidationError.UnknownCategory }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "General",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    isError = categoryError != null,
                    supportingText = categoryError?.let { { Text(it.userMessage, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = fieldColors
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                selectedCategoryId = cat.id
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            var accountExpanded by remember { mutableStateOf(false) }
            val selectedAccount = accounts.find { it.id == selectedAccountId }
            val accountError = errors.firstOrNull { it is ValidationError.UnknownAccount }

            ExposedDropdownMenuBox(
                expanded = accountExpanded,
                onExpandedChange = { accountExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedAccount?.name ?: "Cash",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                    isError = accountError != null,
                    supportingText = accountError?.let { { Text(it.userMessage, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = fieldColors
                )
                ExposedDropdownMenu(
                    expanded = accountExpanded,
                    onDismissRequest = { accountExpanded = false }
                ) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.name) },
                            onClick = {
                                selectedAccountId = acc.id
                                accountExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { newValue ->
                    notes = newValue
                    onEdit(candidate.copy(notes = notes.trim().ifEmpty { null }))
                },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(16.dp))

            val hasFieldErrors = errors.any { it !is ValidationError.DuplicateSuspected }
            val hasDuplicateWarning = errors.any { it is ValidationError.DuplicateSuspected }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.appColors.textSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (hasDuplicateWarning && !hasFieldErrors) {
                            // User is overriding the duplicate warning
                            onOverrideDuplicate()
                        } else {
                            onConfirm()
                        }
                    },
                    enabled = !hasFieldErrors,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.appColors.primary
                    )
                ) {
                    Text(
                        text = when {
                            hasFieldErrors -> "Fix errors to save"
                            hasDuplicateWarning -> "Save Anyway"
                            else -> "Confirm"
                        },
                        color = MaterialTheme.appColors.bgBase
                    )
                }
            }
        }
    }
}
