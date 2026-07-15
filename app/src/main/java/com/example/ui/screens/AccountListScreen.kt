package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Account
import com.example.data.AccountWithBalance
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import com.example.ui.theme.accountPalette
import com.example.ui.theme.appColors
import com.example.ui.viewmodel.LedgerViewModel
import com.example.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    viewModel: LedgerViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val accountsWithBalance by viewModel.allAccountsWithBalance.collectAsStateWithLifecycle()
    val currencySymbol by settingsViewModel.currencySymbol.collectAsStateWithLifecycle()

    var showFormSheet by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var isReorderMode by remember { mutableStateOf(false) }

    // State for Collapsed/Expanded Archived section
    var isArchivedExpanded by remember { mutableStateOf(false) }

    val activeAccounts = remember(accountsWithBalance) {
        accountsWithBalance.filter { !it.account.isArchived }
    }

    val archivedAccounts = remember(accountsWithBalance) {
        accountsWithBalance.filter { it.account.isArchived }
    }

    // Sort Order Reordering callback helper
    fun moveAccount(index: Int, up: Boolean) {
        val list = activeAccounts.map { it.account }.toMutableList()
        if (up && index > 0) {
            val temp = list[index]
            list[index] = list[index - 1]
            list[index - 1] = temp
            viewModel.updateAccountSortOrder(list)
        } else if (!up && index < list.size - 1) {
            val temp = list[index]
            list[index] = list[index + 1]
            list[index + 1] = temp
            viewModel.updateAccountSortOrder(list)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.account_list_title), style = AppTypography.headline3) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.account_list_back_desc))
                    }
                },
                actions = {
                    val doneDesc = stringResource(R.string.account_list_done_desc)
                    val reorderDesc = stringResource(R.string.account_list_reorder_desc)
                    IconButton(onClick = { isReorderMode = !isReorderMode }) {
                        Icon(
                            imageVector = if (isReorderMode) Icons.Default.Check else Icons.Default.Reorder,
                            contentDescription = if (isReorderMode) doneDesc else reorderDesc,
                            tint = if (isReorderMode) MaterialTheme.appColors.primary else MaterialTheme.appColors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.appColors.bgBase,
                    titleContentColor = MaterialTheme.appColors.textPrimary,
                    navigationIconContentColor = MaterialTheme.appColors.textPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    accountToEdit = null
                    showFormSheet = true
                },
                containerColor = MaterialTheme.appColors.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_account_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.account_list_add_desc))
            }
        },
        containerColor = MaterialTheme.appColors.bgBase
    ) { paddingValues ->
        if (accountsWithBalance.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = stringResource(R.string.account_list_no_accounts_desc),
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.appColors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.account_list_empty_msg),
                        style = AppTypography.bodyRegular,
                        color = MaterialTheme.appColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            accountToEdit = null
                            showFormSheet = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary)
                    ) {
                        Text(stringResource(R.string.account_list_add_first_btn), color = Color.White)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Group by Type: CASH, BANK, WALLET, UPI, CREDIT_CARD, SAVINGS, INVESTMENT
                val typesOrder = listOf("CASH", "BANK", "WALLET", "UPI", "CREDIT_CARD", "SAVINGS", "INVESTMENT")
                
                typesOrder.forEach { typeKey ->
                    val typeGroup = activeAccounts.filter { it.account.type.uppercase() == typeKey }
                    if (typeGroup.isNotEmpty()) {
                        item {
                            Text(
                                text = typeKey.replace("_", " "),
                                style = AppTypography.caption,
                                color = MaterialTheme.appColors.textTertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        items(typeGroup.size) { index ->
                            val item = typeGroup[index]
                            AccountRow(
                                accountWithBalance = item,
                                isReorderMode = isReorderMode,
                                currencySymbol = currencySymbol,
                                onUp = { moveAccount(activeAccounts.indexOf(item), up = true) },
                                onDown = { moveAccount(activeAccounts.indexOf(item), up = false) },
                                onClick = { onNavigateToDetail(item.account.id) },
                                onEdit = {
                                    accountToEdit = item.account
                                    showFormSheet = true
                                }
                            )
                        }
                    }
                }

                // Collapsible Archived Section
                if (archivedAccounts.isNotEmpty()) {
                    item {
                        Card(
                            shape = AppShapes.md,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isArchivedExpanded = !isArchivedExpanded }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = null,
                                        tint = MaterialTheme.appColors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.account_list_archived_section_title, archivedAccounts.size),
                                        style = AppTypography.bodySmall,
                                        color = MaterialTheme.appColors.textPrimary
                                    )
                                }
                                Icon(
                                    imageVector = if (isArchivedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.appColors.textSecondary
                                )
                            }
                        }
                    }

                    if (isArchivedExpanded) {
                        items(archivedAccounts.size) { index ->
                            val item = archivedAccounts[index]
                            AccountRow(
                                accountWithBalance = item,
                                isReorderMode = false,
                                currencySymbol = currencySymbol,
                                onClick = { onNavigateToDetail(item.account.id) },
                                onEdit = {
                                    accountToEdit = item.account
                                    showFormSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFormSheet) {
        AccountFormSheet(
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            accountToEdit = accountToEdit,
            onDismiss = { showFormSheet = false }
        )
    }
}

@Composable
fun AccountRow(
    accountWithBalance: AccountWithBalance,
    isReorderMode: Boolean,
    currencySymbol: String,
    onUp: () -> Unit = {},
    onDown: () -> Unit = {},
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val account = accountWithBalance.account
    val color = remember(account.color) {
        try {
            Color(android.graphics.Color.parseColor(account.color))
        } catch (e: Exception) {
            Color(0xFF6200EE)
        }
    }

    Card(
        shape = AppShapes.xl,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_row_${account.id}")
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored circle with emoji
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (account.icon ?: "💰").ifBlank { "💰" },
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = AppTypography.headline3,
                    color = MaterialTheme.appColors.textPrimary
                )
                Text(
                    text = account.type,
                    style = AppTypography.caption,
                    color = MaterialTheme.appColors.textTertiary
                )
            }

            if (isReorderMode) {
                IconButton(onClick = onUp) {
                    Icon(Icons.Default.ArrowDropUp, contentDescription = stringResource(R.string.account_list_move_up_desc), tint = MaterialTheme.appColors.textSecondary)
                }
                IconButton(onClick = onDown) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.account_list_move_down_desc), tint = MaterialTheme.appColors.textSecondary)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    val balText = String.format("%.2f", accountWithBalance.balance)
                    Text(
                        text = "$currencySymbol$balText",
                        style = AppTypography.amount,
                        fontWeight = FontWeight.Bold,
                        color = if (accountWithBalance.balance >= 0) MaterialTheme.appColors.income else MaterialTheme.appColors.expense
                    )
                    if (account.excludeFromTotal) {
                        Text(
                            text = stringResource(R.string.account_list_excluded_badge),
                            style = AppTypography.caption,
                            color = MaterialTheme.appColors.expense
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.account_list_edit_header), tint = MaterialTheme.appColors.textTertiary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormSheet(
    viewModel: LedgerViewModel,
    settingsViewModel: SettingsViewModel,
    accountToEdit: Account?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accountsWithBalance by viewModel.allAccountsWithBalance.collectAsStateWithLifecycle()
    val baseCurrency by settingsViewModel.currencySymbol.collectAsStateWithLifecycle()

    val nameRequiredToast = stringResource(R.string.account_list_name_required_toast)
    val editHeader = stringResource(R.string.account_list_edit_header)
    val addHeader = stringResource(R.string.account_list_add_header)

    var name by remember { mutableStateOf(accountToEdit?.name ?: "") }
    var type by remember { mutableStateOf(accountToEdit?.type ?: "CASH") }
    var startingBalanceStr by remember { mutableStateOf(accountToEdit?.startingBalance?.toString() ?: "0.0") }
    var selectedColor by remember { mutableStateOf(accountToEdit?.color ?: accountPalette.first()) }
    var selectedEmoji by remember { mutableStateOf(accountToEdit?.icon ?: "💵") }
    var excludeFromTotal by remember { mutableStateOf(accountToEdit?.excludeFromTotal ?: false) }

    var showDeleteChooser by remember { mutableStateOf(false) }

    val types = listOf(
        Pair("CASH", "💵"),
        Pair("BANK", "🏦"),
        Pair("WALLET", "👛"),
        Pair("UPI", "📱"),
        Pair("CREDIT_CARD", "💳"),
        Pair("SAVINGS", "🐷"),
        Pair("INVESTMENT", "📈")
    )

    val emojis = listOf(
        "💵", "🏦", "👛", "📱", "💳", "🐷", "📈", "💰", "💵", "🤑",
        "🏪", "🚘", "🏠", "✈️", "☕", "🍕", "👔", "🍿", "🏀", "🎁",
        "💊", "🧼", "🐶", "🐱", "🌹", "⚡", "🔥", "🌈", "💻", "💼"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.appColors.bgSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
            Text(
                text = if (accountToEdit != null) editHeader else addHeader,
                style = AppTypography.headline3,
                color = MaterialTheme.appColors.textPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 30) name = it },
                label = { Text(stringResource(R.string.account_list_name_field)) },
                modifier = Modifier.fillMaxWidth().testTag("account_name_field"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.appColors.primary,
                    unfocusedBorderColor = MaterialTheme.appColors.borderLight
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = startingBalanceStr,
                    onValueChange = { startingBalanceStr = it },
                    label = { Text(stringResource(R.string.account_list_balance_field, baseCurrency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("account_balance_field"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(12.dp))
                // Exclude switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.account_list_exclude_label), style = AppTypography.caption, color = MaterialTheme.appColors.textSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = excludeFromTotal,
                        onCheckedChange = { excludeFromTotal = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emoji Selection
            Text(stringResource(R.string.account_list_icon_title), style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                emojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (selectedEmoji == emoji) MaterialTheme.appColors.primary.copy(alpha = 0.2f) else MaterialTheme.appColors.bgSurface2)
                            .clickable { selectedEmoji = emoji }
                            .border(
                                1.5.dp,
                                if (selectedEmoji == emoji) MaterialTheme.appColors.primary else Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Color Swatches Selection
            Text(stringResource(R.string.account_list_color_title), style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accountPalette.forEach { hex ->
                    val color = remember { Color(android.graphics.Color.parseColor(hex)) }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColor = hex }
                            .border(
                                2.dp,
                                if (selectedColor == hex) MaterialTheme.appColors.textPrimary else Color.Transparent,
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Account Type Selector (7 grid chips)
            Text(stringResource(R.string.account_list_type_title), style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                types.forEach { pair ->
                    val isSelected = type.uppercase() == pair.first
                    FilterChip(
                        selected = isSelected,
                        onClick = { type = pair.first },
                        label = { Text("${pair.second} ${pair.first}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.appColors.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.appColors.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Buttons / Delete
            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, nameRequiredToast, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val startingBal = startingBalanceStr.toDoubleOrNull() ?: 0.0
                    if (accountToEdit != null) {
                        viewModel.updateAccount(
                            accountToEdit.copy(
                                name = name.trim(),
                                type = type.trim(),
                                startingBalance = startingBal,
                                color = selectedColor,
                                icon = selectedEmoji,
                                excludeFromTotal = excludeFromTotal
                            )
                        )
                    } else {
                        viewModel.addAccount(
                            name = name,
                            type = type,
                            startingBalance = startingBal,
                            color = selectedColor,
                            icon = selectedEmoji,
                            excludeFromTotal = excludeFromTotal
                        )
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_account_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary)
            ) {
                Text(stringResource(R.string.account_list_save_btn), color = Color.White)
            }

            if (accountToEdit != null) {
                Spacer(modifier = Modifier.height(12.dp))

                // Archive / Unarchive Button
                Button(
                    onClick = {
                        viewModel.archiveAccount(accountToEdit.id, !accountToEdit.isArchived)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.bgSurface2, contentColor = MaterialTheme.appColors.textPrimary)
                ) {
                    Text(if (accountToEdit.isArchived) stringResource(R.string.account_list_unarchive_btn) else stringResource(R.string.account_list_archive_btn))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Delete Button
                Button(
                    onClick = { showDeleteChooser = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.expense)
                ) {
                    Text(stringResource(R.string.account_list_delete_btn), color = Color.White)
                }
            }
        }
    }

    if (showDeleteChooser && accountToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteChooser = false },
            title = { Text(stringResource(R.string.account_list_delete_confirm_title, accountToEdit.name)) },
            text = { Text(stringResource(R.string.account_list_delete_confirm_msg)) },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option 1: Reassign to next account
                    val fallbackAccount = accountsWithBalance.find { it.account.id != accountToEdit.id && !it.account.isArchived }?.account
                    if (fallbackAccount != null) {
                        Button(
                            onClick = {
                                viewModel.deleteAccount(accountToEdit.id, reassignToAccountId = fallbackAccount.id)
                                showDeleteChooser = false
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary)
                        ) {
                            Text(stringResource(R.string.account_list_reassign_btn, fallbackAccount.name), color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Option 2: Delete All
                    Button(
                        onClick = {
                            viewModel.deleteAccount(accountToEdit.id, deleteAllTransactions = true)
                            showDeleteChooser = false
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.expense)
                    ) {
                        Text(stringResource(R.string.account_list_delete_txs_btn), color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showDeleteChooser = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        )
    }
}
