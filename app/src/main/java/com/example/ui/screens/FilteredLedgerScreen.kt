package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.Transaction
import com.example.data.TransactionRepository
import com.example.ui.navigation.FilteredLedger
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredLedgerScreen(
    filter: FilteredLedger,
    transactionRepository: TransactionRepository,
    onBack: () -> Unit = {}
) {
    var results by remember { mutableStateOf<List<Transaction>>(emptyList()) }

    LaunchedEffect(filter) {
        results = if (!filter.transactionIds.isNullOrBlank()) {
            // Exact ID lookup — always matches the aggregate headline count
            val ids = filter.transactionIds.split(",").filter { it.isNotBlank() }
            transactionRepository.getByIds(ids)
        } else {
            val start = filter.timeRangeStart ?: 0L
            val end   = filter.timeRangeEnd   ?: Long.MAX_VALUE
            transactionRepository.getByDateRange(start, end).filter { tx ->
                (filter.entity    == null || tx.title.equals(filter.entity, ignoreCase = true)) &&
                (filter.direction == null || tx.type == filter.direction) &&
                (filter.categoryId == null || tx.categoryId == filter.categoryId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildString {
                            append(stringResource(R.string.filtered_ledger_title))
                            filter.entity?.let    { append(" • $it") }
                            filter.direction?.let { append(" • $it") }
                        },
                        style = AppTypography.headline3,
                        color = MaterialTheme.appColors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.appColors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.appColors.bgSurface
                )
            )
        },
        containerColor = MaterialTheme.appColors.bgBase
    ) { innerPadding ->
        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.dashboard_no_transactions),
                    style = AppTypography.bodyRegular,
                    color = MaterialTheme.appColors.textTertiary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = AppSpacing.lg),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AppSpacing.sm)
            ) {
                items(results) { tx ->
                    val isOutgoing = tx.type == "EXPENSE" || tx.type == "TRANSFER"
                    val sign       = if (isOutgoing) "-" else "+"
                    val typeLabel  = tx.type.lowercase().replaceFirstChar { it.uppercase() }
                    val amountColor = if (isOutgoing) MaterialTheme.appColors.expense else MaterialTheme.appColors.income
                    ListItem(
                        headlineContent = {
                            Text(
                                text = tx.title,
                                style = AppTypography.bodyLarge,
                                color = MaterialTheme.appColors.textPrimary
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "$typeLabel • ${sign}₹${String.format("%.0f", tx.amount)}",
                                style = AppTypography.bodySmall,
                                color = amountColor
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.appColors.bgSurface
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.appColors.borderLight)
                }
            }
        }
    }
}
