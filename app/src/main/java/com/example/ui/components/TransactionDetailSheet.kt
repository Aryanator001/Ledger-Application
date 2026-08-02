package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Account
import com.example.data.Category
import com.example.data.Transaction
import com.example.ui.theme.appColors
import com.example.ui.theme.AppTypography
import com.example.ui.viewmodel.LedgerViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import com.example.R

@Composable
fun CategoryIconDisplay(iconName: String, modifier: Modifier = Modifier) {
    when (iconName) {
        "restaurant" -> Icon(Icons.Default.Restaurant, contentDescription = null, modifier = modifier)
        "directions_car" -> Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = modifier)
        "shopping_bag" -> Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = modifier)
        "receipt_long" -> Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = modifier)
        "movie" -> Icon(Icons.Default.Movie, contentDescription = null, modifier = modifier)
        "medical_services" -> Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = modifier)
        "school" -> Icon(Icons.Default.School, contentDescription = null, modifier = modifier)
        "face" -> Icon(Icons.Default.Face, contentDescription = null, modifier = modifier)
        "flight" -> Icon(Icons.Default.Flight, contentDescription = null, modifier = modifier)
        "payments" -> Icon(Icons.Default.Payments, contentDescription = null, modifier = modifier)
        "work" -> Icon(Icons.Default.Work, contentDescription = null, modifier = modifier)
        "store" -> Icon(Icons.Default.Store, contentDescription = null, modifier = modifier)
        "trending_up" -> Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = modifier)
        "card_giftcard" -> Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = modifier)
        "more_horiz" -> Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = modifier)
        else -> Text(text = iconName, fontSize = 24.sp)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    viewModel: LedgerViewModel,
    initialAmount: Double? = null,
    initialType: String? = null,
    existingTransaction: Transaction? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val allAccountsWithBalance by viewModel.allAccountsWithBalance.collectAsStateWithLifecycle()
    val allAccounts = remember(allAccountsWithBalance) { allAccountsWithBalance.map { it.account } }
    val context = LocalContext.current

    var amountStr by remember { mutableStateOf(existingTransaction?.amount?.toString()?.removeSuffix(".0") ?: initialAmount?.toString()?.removeSuffix(".0") ?: "") }
    var type by remember { mutableStateOf(existingTransaction?.type ?: initialType ?: "EXPENSE") }
    var categoryId by remember { mutableStateOf(existingTransaction?.categoryId ?: "") }
    var accountId by remember { mutableStateOf(existingTransaction?.accountId ?: "") }
    var toAccountId by remember { mutableStateOf(existingTransaction?.toAccountId ?: "") }
    var dateMs by remember { mutableStateOf(existingTransaction?.date ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(existingTransaction?.description ?: "") }
    var tags by remember { mutableStateOf(existingTransaction?.tags ?: "") }

    LaunchedEffect(allAccounts) {
        if (accountId.isEmpty() && allAccounts.isNotEmpty()) {
            accountId = allAccounts.firstOrNull { it.id == "default_cash" }?.id ?: allAccounts.first().id
        }
    }

    var showCategorySheet by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryIcon by remember { mutableStateOf("🍔") }
    val presetEmojis = remember { listOf("🍔", "🍕", "🚗", "💡", "🎬", "🛍️", "🩺", "✈️", "📚", "🏋️", "🎁", "💰") }
    var newCategoryColor by remember { mutableStateOf("#FF5722") }
    val presetColors = remember { listOf("#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#00BCD4", "#4CAF50", "#FFEB3B", "#FF9800", "#795548", "#607D8B") }

    // Init category id if empty
    LaunchedEffect(type, allCategories) {
        if (categoryId.isEmpty() && allCategories.isNotEmpty()) {
            categoryId = allCategories.firstOrNull { it.type == type || it.type == "BOTH" }?.id ?: ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.appColors.bgSurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (existingTransaction != null) stringResource(R.string.sheet_edit_transaction) else stringResource(R.string.sheet_new_transaction),
                style = AppTypography.headline3,
                color = MaterialTheme.appColors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Amount field
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text(stringResource(R.string.sheet_amount_label), color = MaterialTheme.appColors.textSecondary) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.appColors.textPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.appColors.primary,
                    unfocusedBorderColor = MaterialTheme.appColors.borderLight
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Type Pills
            Row(modifier = Modifier.fillMaxWidth()) {
                val types = listOf("EXPENSE", "INCOME", "TRANSFER")
                types.forEach { t ->
                    val isSelected = type == t
                    val bgColor = if (isSelected) MaterialTheme.appColors.primary else MaterialTheme.appColors.bgSurface2
                    val textColor = if (isSelected) Color.White else MaterialTheme.appColors.textSecondary
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (t != "TRANSFER") 8.dp else 0.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .clickable { type = t }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (t) {
                                "EXPENSE" -> stringResource(R.string.expense)
                                "INCOME" -> stringResource(R.string.income)
                                else -> stringResource(R.string.transaction_type_transfer)
                            },
                            style = AppTypography.caption,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Button
            if (type != "TRANSFER") {
                val selectedCat = allCategories.find { it.id == categoryId }
                OutlinedButton(
                    onClick = { showCategorySheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.appColors.textPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.appColors.borderLight)
                ) {
                    Text(selectedCat?.name ?: stringResource(R.string.sheet_select_category))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Account Dropdown
            var accountMenuExpanded by remember { mutableStateOf(false) }
            val selectedAccount = allAccounts.find { it.id == accountId }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { accountMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.appColors.textPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.appColors.borderLight)
                ) {
                    Text(selectedAccount?.name ?: stringResource(R.string.sheet_select_account))
                }
                DropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false }
                ) {
                    allAccounts.filter { !it.isArchived }.forEach { account ->
                        val bal = allAccountsWithBalance.find { it.account.id == account.id }?.balance ?: 0.0
                        DropdownMenuItem(
                            text = { Text("${account.name} (₹$bal)") },
                            onClick = {
                                accountId = account.id
                                accountMenuExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // To Account
            if (type == "TRANSFER") {
                var toAccountMenuExpanded by remember { mutableStateOf(false) }
                val selectedToAccount = allAccounts.find { it.id == toAccountId }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { toAccountMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.appColors.textPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.appColors.borderLight)
                    ) {
                        Text(selectedToAccount?.name ?: stringResource(R.string.sheet_select_to_account))
                    }
                    DropdownMenu(
                        expanded = toAccountMenuExpanded,
                        onDismissRequest = { toAccountMenuExpanded = false }
                    ) {
                        allAccounts.filter { !it.isArchived && it.id != accountId }.forEach { account ->
                            val bal = allAccountsWithBalance.find { it.account.id == account.id }?.balance ?: 0.0
                            DropdownMenuItem(
                                text = { Text("${account.name} (₹$bal)") },
                                onClick = {
                                    toAccountId = account.id
                                    toAccountMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Optional Details
            var showDetails by remember { mutableStateOf(false) }
            Text(
                stringResource(R.string.sheet_optional_details),
                style = AppTypography.caption,
                color = MaterialTheme.appColors.primary,
                modifier = Modifier.clickable { showDetails = !showDetails }
            )
            
            if (showDetails) {
                Spacer(modifier = Modifier.height(8.dp))
                val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.US)
                val sdfTime = SimpleDateFormat("hh:mm a", Locale.US)
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Simple text fields for mock date time for now
                    OutlinedTextField(
                        value = sdfDate.format(Date(dateMs)),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.sheet_date)) },
                        enabled = false,
                        modifier = Modifier.weight(1f).clickable { /* TODO DatePicker */ }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = sdfTime.format(Date(dateMs)),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.sheet_time)) },
                        enabled = false,
                        modifier = Modifier.weight(1f).clickable { /* TODO TimePicker */ }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 200) note = it },
                    label = { Text(stringResource(R.string.sheet_note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.sheet_tags)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        Toast.makeText(context, context.getString(R.string.toast_amount_invalid), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (type != "TRANSFER" && categoryId.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.toast_select_category), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (accountId.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.toast_select_account), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (type == "TRANSFER" && toAccountId.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.toast_select_dest_account), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (type == "TRANSFER" && accountId == toAccountId) {
                        Toast.makeText(context, context.getString(R.string.toast_same_account_transfer), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (type == "TRANSFER") {
                        val sourceAccountWithBalance = allAccountsWithBalance.find { it.account.id == accountId }
                        if (sourceAccountWithBalance != null && amount > sourceAccountWithBalance.balance) {
                            Toast.makeText(context, context.getString(R.string.toast_transfer_exceeds_balance), Toast.LENGTH_LONG).show()
                        }
                    }
                    if (dateMs > System.currentTimeMillis()) {
                        Toast.makeText(context, context.getString(R.string.toast_future_date), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    if (existingTransaction != null) {
                        viewModel.updateTransaction(
                            existingTransaction.copy(
                                amount = amount,
                                type = type,
                                categoryId = if (type == "TRANSFER") "general" else categoryId, // Assuming general for transfer for now
                                accountId = accountId,
                                toAccountId = if (type == "TRANSFER") toAccountId else null,
                                description = note.ifBlank { null },
                                tags = tags,
                                date = dateMs
                            )
                        )
                    } else {
                        val categoryName = if (type == "TRANSFER") {
                            context.getString(R.string.transaction_type_transfer)
                        } else {
                            allCategories.firstOrNull { it.id == categoryId }?.name ?: context.getString(R.string.transaction_default_title)
                        }
                        viewModel.addTransaction(
                            title = categoryName,
                            amount = amount,
                            type = type,
                            categoryId = if (type == "TRANSFER") "general" else categoryId,
                            accountId = accountId,
                            toAccountId = if (type == "TRANSFER") toAccountId else null,
                            description = note.ifBlank { null },
                            tags = tags,
                            date = dateMs
                        )
                    }
                    
                    Toast.makeText(context, context.getString(R.string.toast_tx_saved), Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary)
            ) {
                Text(stringResource(R.string.action_save), color = Color.White)
            }
        }
    }

    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            containerColor = MaterialTheme.appColors.bgSurface
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)
            ) {
                val filteredCategories = allCategories.filter { it.type == type || it.type == "BOTH" }
                items(filteredCategories) { cat ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                categoryId = cat.id
                                showCategorySheet = false
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(cat.colorHex)).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CategoryIconDisplay(iconName = cat.icon)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(cat.name, style = AppTypography.caption, color = MaterialTheme.appColors.textPrimary, maxLines = 1)
                    }
                }
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                showCategorySheet = false
                                showAddCategoryDialog = true
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.appColors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.sheet_add_new_category_desc),
                                tint = MaterialTheme.appColors.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.sheet_add_new), style = AppTypography.caption, color = MaterialTheme.appColors.primary, maxLines = 1)
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(stringResource(R.string.sheet_add_custom_category), style = AppTypography.headline3, color = MaterialTheme.appColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text(stringResource(R.string.sheet_category_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.appColors.primary,
                            unfocusedBorderColor = MaterialTheme.appColors.borderLight
                        )
                    )

                    Text(stringResource(R.string.sheet_select_emoji_icon), style = AppTypography.caption, color = MaterialTheme.appColors.textSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryIcon,
                            onValueChange = { if (it.length <= 2) newCategoryIcon = it },
                            label = { Text(stringResource(R.string.sheet_custom_icon)) },
                            singleLine = true,
                            modifier = Modifier.width(80.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.appColors.primary,
                                unfocusedBorderColor = MaterialTheme.appColors.borderLight
                            )
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.height(80.dp).weight(1f)
                        ) {
                            items(presetEmojis) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (newCategoryIcon == emoji) MaterialTheme.appColors.primary.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable { newCategoryIcon = emoji }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }

                    Text(stringResource(R.string.sheet_select_color), style = AppTypography.caption, color = MaterialTheme.appColors.textSecondary)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.height(72.dp)
                    ) {
                        items(presetColors) { colorHex ->
                            val isSelected = newCategoryColor == colorHex
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                    .clickable { newCategoryColor = colorHex }
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.appColors.textPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCategoryName.trim().isBlank()) {
                            Toast.makeText(context, context.getString(R.string.toast_name_empty), Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val finalIcon = newCategoryIcon.ifBlank { newCategoryName.take(1) }
                        val generatedId = java.util.UUID.randomUUID().toString()
                        
                        viewModel.addCategory(
                            id = generatedId,
                            name = newCategoryName,
                            icon = finalIcon,
                            colorHex = newCategoryColor,
                            type = type
                        )
                        categoryId = generatedId
                        showAddCategoryDialog = false
                        newCategoryName = ""
                        Toast.makeText(context, context.getString(R.string.toast_category_added), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.action_add), color = MaterialTheme.appColors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.appColors.textSecondary)
                }
            },
            containerColor = MaterialTheme.appColors.bgSurface
        )
    }
}
