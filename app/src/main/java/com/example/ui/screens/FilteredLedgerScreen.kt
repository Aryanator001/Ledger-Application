package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.Transaction
import com.example.data.TransactionRepository
import com.example.ui.navigation.FilteredLedger

@Composable
fun FilteredLedgerScreen(
    filter: FilteredLedger,
    transactionRepository: TransactionRepository
) {
    var results by remember { mutableStateOf<List<Transaction>>(emptyList()) }

    LaunchedEffect(filter) {
        val start = filter.timeRangeStart ?: 0L
        val end = filter.timeRangeEnd ?: Long.MAX_VALUE
        results = transactionRepository.getByDateRange(start, end).filter { tx ->
            (filter.entity == null || tx.title.contains(filter.entity, ignoreCase = true)) &&
            (filter.direction == null || tx.type == filter.direction) &&
            (filter.categoryId == null || tx.categoryId == filter.categoryId)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = buildString {
                append("Filtered results")
                filter.entity?.let { append(" • $it") }
                filter.direction?.let { append(" • $it") }
                filter.categoryId?.let { append(" • $it") }
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(results) { tx ->
                ListItem(
                    headlineContent = { Text(tx.title) },
                    supportingContent = { Text("${"$"}{tx.type} • ₹${"$"}{tx.amount}") }
                )
                HorizontalDivider()
            }
        }
    }
}
