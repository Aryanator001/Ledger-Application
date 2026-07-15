package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.cards.TransactionCard
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors
import com.example.ui.viewmodel.LedgerViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.components.TransactionDetailSheet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: String,
    viewModel: LedgerViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val currencySymbol by settingsViewModel.currencySymbol.collectAsStateWithLifecycle()
    val allAccountsWithBalance by viewModel.allAccountsWithBalance.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    
    val accountWithBalance = remember(allAccountsWithBalance, accountId) {
        allAccountsWithBalance.find { it.account.id == accountId }
    }

    // Set selected account in ViewModel
    LaunchedEffect(accountId) {
        viewModel.selectAccount(accountId)
    }

    val transactions by viewModel.transactionsByAccount.collectAsStateWithLifecycle()

    // Monthly filter state
    var selectedMonthIndex by remember { mutableStateOf(-1) } // -1 means All Time
    var isMonthDropdownExpanded by remember { mutableStateOf(false) }

    val monthJanuary = stringResource(R.string.month_january)
    val monthFebruary = stringResource(R.string.month_february)
    val monthMarch = stringResource(R.string.month_march)
    val monthApril = stringResource(R.string.month_april)
    val monthMay = stringResource(R.string.month_may)
    val monthJune = stringResource(R.string.month_june)
    val monthJuly = stringResource(R.string.month_july)
    val monthAugust = stringResource(R.string.month_august)
    val monthSeptember = stringResource(R.string.month_september)
    val monthOctober = stringResource(R.string.month_october)
    val monthNovember = stringResource(R.string.month_november)
    val monthDecember = stringResource(R.string.month_december)
    val monthsList = remember(monthJanuary, monthFebruary, monthMarch, monthApril, monthMay, monthJune,
        monthJuly, monthAugust, monthSeptember, monthOctober, monthNovember, monthDecember) {
        listOf(monthJanuary, monthFebruary, monthMarch, monthApril, monthMay, monthJune,
            monthJuly, monthAugust, monthSeptember, monthOctober, monthNovember, monthDecember)
    }

    val filteredTransactions = remember(transactions, selectedMonthIndex) {
        if (selectedMonthIndex == -1) {
            transactions
        } else {
            val cal = Calendar.getInstance()
            transactions.filter { tx ->
                cal.timeInMillis = tx.date
                cal.get(Calendar.MONTH) == selectedMonthIndex
            }
        }
    }

    // Account specific metrics based on CURRENT filtered transactions
    val accountIncome = remember(filteredTransactions) {
        filteredTransactions.filter { 
            it.type == "INCOME" || it.type == "CREDIT" || (it.type == "TRANSFER" && it.toAccountId == accountId)
        }.sumOf { it.amount }
    }

    val accountExpense = remember(filteredTransactions) {
        filteredTransactions.filter { 
            it.type == "EXPENSE" || it.type == "DEBIT" || (it.type == "TRANSFER" && it.accountId == accountId)
        }.sumOf { it.amount }
    }

    var showAddTransactionSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = accountWithBalance?.account?.name ?: stringResource(R.string.account_detail_fallback_title),
                        style = AppTypography.headline3
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.account_detail_back_desc))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.appColors.bgBase,
                    titleContentColor = MaterialTheme.appColors.textPrimary,
                    navigationIconContentColor = MaterialTheme.appColors.textPrimary
                )
            )
        },
        containerColor = MaterialTheme.appColors.bgBase
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Live account header metrics
            Card(
                shape = AppShapes.xl,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.account_detail_balance_title),
                        style = AppTypography.caption,
                        color = MaterialTheme.appColors.textTertiary
                    )
                    Text(
                        text = "$currencySymbol${String.format("%.2f", accountWithBalance?.balance ?: 0.0)}",
                        style = AppTypography.amountLarge,
                        color = MaterialTheme.appColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.account_detail_inflow_title), style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary)
                            Text(
                                "+$currencySymbol${String.format("%.2f", accountIncome)}",
                                style = AppTypography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.appColors.income
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.account_detail_outflow_title), style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary)
                            Text(
                                "-$currencySymbol${String.format("%.2f", accountExpense)}",
                                style = AppTypography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.appColors.expense
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Monthly Filter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.account_detail_txs_title),
                    style = AppTypography.headline3,
                    color = MaterialTheme.appColors.textPrimary
                )

                Box {
                    Button(
                        onClick = { isMonthDropdownExpanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.appColors.bgSurface2,
                            contentColor = MaterialTheme.appColors.textPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedMonthIndex == -1) stringResource(R.string.account_detail_all_time_option) else monthsList[selectedMonthIndex], fontSize = 12.sp)
                    }

                    DropdownMenu(
                        expanded = isMonthDropdownExpanded,
                        onDismissRequest = { isMonthDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_detail_all_time_option)) },
                            onClick = {
                                selectedMonthIndex = -1
                                isMonthDropdownExpanded = false
                            }
                        )
                        monthsList.forEachIndexed { index, monthName ->
                            DropdownMenuItem(
                                text = { Text(monthName) },
                                onClick = {
                                    selectedMonthIndex = index
                                    isMonthDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.account_detail_empty_msg),
                            style = AppTypography.bodyRegular,
                            color = MaterialTheme.appColors.textTertiary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddTransactionSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.account_detail_prefill_btn), color = Color.White)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredTransactions) { tx ->
                        val cat = remember(tx.categoryId, allCategories) {
                            allCategories.find { it.id == tx.categoryId }
                        }
                        TransactionCard(
                            transaction = tx,
                            category = cat,
                            currencySymbol = currencySymbol,
                            onClick = { }
                        )
                    }
                }
            }
        }
    }

    if (showAddTransactionSheet) {
        TransactionDetailSheet(
            viewModel = viewModel,
            initialAmount = null,
            existingTransaction = null,
            onDismiss = { showAddTransactionSheet = false }
        )
    }
}
